package ar.sistemaodontologico.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import ar.sistemaodontologico.auth.application.UsuarioRepositorio;
import ar.sistemaodontologico.compartido.IntegracionPostgresTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Date;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Spec registro-odontologo (C-04, tareas 3.2 a 3.4 y 2.3). Datos ficticios (regla dura 17). El reloj queda fijo
 * el 2026-12-01 a las 12:00 UTC, que en Buenos Aires también es 2026-12-01.
 */
@Import(RegistroOdontologoTest.Configuracion.class)
class RegistroOdontologoTest extends IntegracionPostgresTest {

    static final Instant AHORA = Instant.parse("2026-12-01T12:00:00Z");

    @TestConfiguration
    static class Configuracion {

        @Bean
        @Primary
        Clock relojFijo() {
            return Clock.fixed(AHORA, ZoneOffset.UTC);
        }
    }

    private static final String RUTA = "/api/v1/auth/registro";
    private static final String CONTRASENA = "una-clave-larga";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    @MockitoSpyBean
    private UsuarioRepositorio usuarios;

    @Test
    void registroExitoso() throws Exception {
        String email = email();

        ResponseEntity<String> respuesta = registrar(email, CONTRASENA, "Consultorio Demo Sur " + sufijo(), true);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(respuesta.getHeaders().get(HttpHeaders.SET_COOKIE)).isNull();
        JsonNode cuerpo = json.readTree(respuesta.getBody());
        assertThat(cuerpo.has("accessToken")).isFalse();
        UUID consultorio = UUID.fromString(cuerpo.path("consultorioId").asText());
        Map<String, Object> fila = jdbc.queryForMap("SELECT plan, prueba_hasta, slug FROM consultorio WHERE id = ?", consultorio);
        assertThat(fila.get("plan")).isEqualTo("PRUEBA");
        assertThat(((Date) fila.get("prueba_hasta")).toLocalDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(fila.get("slug")).isEqualTo(cuerpo.path("slug").asText());
        assertThat(jdbc.queryForObject("SELECT consultorio_id FROM usuario WHERE id = ?", UUID.class,
                UUID.fromString(cuerpo.path("usuarioId").asText()))).isEqualTo(consultorio);
    }

    @Test
    void laContrasenaSeGuardaSoloComoHashBcryptDeCostoDoce() {
        String email = email();

        registrar(email, CONTRASENA, "Consultorio Hash " + sufijo(), true);

        String hash = jdbc.queryForObject("SELECT password_hash FROM usuario WHERE email = ?", String.class, email);
        assertThat(hash).startsWith("$2a$12$").doesNotContain(CONTRASENA);
    }

    @Test
    void registraLaVersionDeTerminosYElInstanteDeAceptacion() {
        String email = email();

        registrar(email, CONTRASENA, "Consultorio Terminos " + sufijo(), true);

        Map<String, Object> fila = jdbc.queryForMap(
                "SELECT terminos_version, terminos_aceptados_at FROM usuario WHERE email = ?", email);
        assertThat(fila.get("terminos_version")).isEqualTo("2026-11-borrador");
        assertThat(((java.sql.Timestamp) fila.get("terminos_aceptados_at")).toInstant()).isEqualTo(AHORA);
    }

    @Test
    void emailRepetidoConOtrasMayusculasNoCreaNada() throws Exception {
        String email = email();
        registrar(email, CONTRASENA, "Consultorio Original " + sufijo(), true);
        int consultoriosAntes = contarConsultorios();

        ResponseEntity<String> respuesta = registrar(email.toUpperCase(), CONTRASENA, "Otro " + sufijo(), true);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(codigo(respuesta)).isEqualTo("EMAIL_EN_USO");
        assertThat(contarConsultorios()).isEqualTo(consultoriosAntes);
    }

    @Test
    void contrasenaDeNueveCaracteres() throws Exception {
        ResponseEntity<String> respuesta = registrar(email(), "123456789", "Consultorio " + sufijo(), true);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(codigo(respuesta)).isEqualTo("PASSWORD_DEBIL");
    }

    @Test
    void contrasenaDeMasDeSetentaYDosBytesNoDaErrorInterno() throws Exception {
        for (String larga : new String[] {"a".repeat(73), "ñ".repeat(37)}) {
            ResponseEntity<String> respuesta = registrar(email(), larga, "Consultorio " + sufijo(), true);

            assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(codigo(respuesta)).isEqualTo("PASSWORD_DEBIL");
        }
    }

    @Test
    void elEmailSeNormalizaUnaSolaVez() throws Exception {
        String email = email();
        registrar("  " + email + "  ", CONTRASENA, "Consultorio Espacios " + sufijo(), true);

        ResponseEntity<String> repetido = registrar(email, CONTRASENA, "Otro " + sufijo(), true);

        assertThat(jdbc.queryForObject("SELECT email FROM usuario WHERE email = ?", String.class, email)).isEqualTo(email);
        assertThat(codigo(repetido)).isEqualTo("EMAIL_EN_USO");
    }

    @Test
    void contrasenaIgualAlEmail() throws Exception {
        String email = email();

        ResponseEntity<String> respuesta = registrar(email, email, "Consultorio " + sufijo(), true);

        assertThat(codigo(respuesta)).isEqualTo("PASSWORD_DEBIL");
    }

    @Test
    void sinAceptarTerminosNoCreaNada() throws Exception {
        int antes = contarConsultorios();

        ResponseEntity<String> rechazo = registrar(email(), CONTRASENA, "Consultorio " + sufijo(), false);
        ResponseEntity<String> ausente = postear(Map.of("nombre", "Ana", "apellido", "Prueba", "email", email(),
                "password", CONTRASENA, "nombreConsultorio", "Consultorio " + sufijo()));

        assertThat(rechazo.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(codigo(rechazo)).isEqualTo("TERMINOS_NO_ACEPTADOS");
        assertThat(codigo(ausente)).isEqualTo("TERMINOS_NO_ACEPTADOS");
        assertThat(contarConsultorios()).isEqualTo(antes);
    }

    @Test
    void emailMalFormado() throws Exception {
        ResponseEntity<String> respuesta = registrar("no-es-un-email", CONTRASENA, "Consultorio " + sufijo(), true);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode cuerpo = json.readTree(respuesta.getBody());
        assertThat(cuerpo.path("codigo").asText()).isEqualTo("VALIDACION");
        assertThat(cuerpo.path("errores").toString()).contains("\"campo\":\"email\"");
    }

    @Test
    void slugYaTomadoUsaElSufijoLibreMasBajo() throws Exception {
        String base = "demo-sur-" + sufijo();
        insertarConsultorioConSlug(base);
        insertarConsultorioConSlug(base + "-2");

        ResponseEntity<String> respuesta = registrar(email(), CONTRASENA, base.replace('-', ' '), true);

        assertThat(json.readTree(respuesta.getBody()).path("slug").asText()).isEqualTo(base + "-3");
    }

    @Test
    void unaFallaAlCrearElUsuarioNoDejaElConsultorio() {
        String nombre = "Consultorio Parcial " + sufijo();
        doThrow(new IllegalStateException("falla simulada")).when(usuarios).guardarNuevo(any());

        ResponseEntity<String> respuesta = registrar(email(), CONTRASENA, nombre, true);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM consultorio WHERE nombre = ?", Integer.class, nombre)).isZero();
    }

    @Test
    void dosRegistrosSimultaneosConElMismoNombreObtienenSlugsDistintos() throws Exception {
        String nombre = "Consultorio Carrera " + sufijo();

        List<ResponseEntity<String>> respuestas = enParalelo(
                () -> registrar(email(), CONTRASENA, nombre, true),
                () -> registrar(email(), CONTRASENA, nombre, true));

        assertThat(respuestas).allSatisfy(r -> assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED));
        assertThat(json.readTree(respuestas.get(0).getBody()).path("slug").asText())
                .isNotEqualTo(json.readTree(respuestas.get(1).getBody()).path("slug").asText());
    }

    // Tarea 2.3: la carrera entre dos registros con el mismo email la resuelve uk_usuario_email.
    @Test
    void dosRegistrosSimultaneosConElMismoEmailDejanUnoSolo() throws Exception {
        String email = email();

        List<ResponseEntity<String>> respuestas = enParalelo(
                () -> registrar(email, CONTRASENA, "Consultorio Uno " + sufijo(), true),
                () -> registrar(email, CONTRASENA, "Consultorio Dos " + sufijo(), true));

        assertThat(respuestas).extracting(ResponseEntity::getStatusCode)
                .containsExactlyInAnyOrder(HttpStatus.CREATED, HttpStatus.CONFLICT);
        ResponseEntity<String> conflicto = respuestas.stream()
                .filter(r -> r.getStatusCode() == HttpStatus.CONFLICT).findFirst().orElseThrow();
        assertThat(codigo(conflicto)).isEqualTo("EMAIL_EN_USO");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM usuario WHERE email = ?", Integer.class, email)).isEqualTo(1);
    }

    @SafeVarargs
    private List<ResponseEntity<String>> enParalelo(Callable<ResponseEntity<String>>... tareas) throws Exception {
        ExecutorService hilos = Executors.newFixedThreadPool(tareas.length);
        try {
            List<Future<ResponseEntity<String>>> futuros = new ArrayList<>();
            for (Callable<ResponseEntity<String>> tarea : tareas) {
                futuros.add(hilos.submit(tarea));
            }
            List<ResponseEntity<String>> respuestas = new ArrayList<>();
            for (Future<ResponseEntity<String>> futuro : futuros) {
                respuestas.add(futuro.get());
            }
            return respuestas;
        } finally {
            hilos.shutdown();
        }
    }

    private ResponseEntity<String> registrar(String email, String password, String nombreConsultorio, boolean acepta) {
        return postear(Map.of("nombre", "Ana", "apellido", "Prueba", "email", email, "password", password,
                "nombreConsultorio", nombreConsultorio, "aceptaTerminos", acepta));
    }

    private ResponseEntity<String> postear(Map<String, Object> cuerpo) {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setContentType(MediaType.APPLICATION_JSON);
        return http.postForEntity(RUTA, new HttpEntity<>(cuerpo, cabeceras), String.class);
    }

    private String codigo(ResponseEntity<String> respuesta) throws Exception {
        return json.readTree(respuesta.getBody()).path("codigo").asText();
    }

    private int contarConsultorios() {
        return jdbc.queryForObject("SELECT count(*) FROM consultorio", Integer.class);
    }

    private void insertarConsultorioConSlug(String slug) {
        jdbc.update("INSERT INTO consultorio (id, nombre, slug, plan) VALUES (?, 'Consultorio Demo', ?, 'PRUEBA')",
                UUID.randomUUID(), slug);
    }

    private static String email() {
        return "registro-" + sufijo() + "@example.com";
    }

    private static String sufijo() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
