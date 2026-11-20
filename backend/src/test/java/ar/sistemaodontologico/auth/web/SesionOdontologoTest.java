package ar.sistemaodontologico.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import ar.sistemaodontologico.auth.application.HasheadorDeContrasena;
import ar.sistemaodontologico.auth.domain.HashDeToken;
import ar.sistemaodontologico.compartido.IntegracionPostgresTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import pruebas.seguridad.ControladorPrivadoDePrueba;

/** Spec sesion-odontologo (C-04, tareas 4.2 a 4.5). Datos ficticios (regla dura 17). */
@Import(ControladorPrivadoDePrueba.class)
class SesionOdontologoTest extends IntegracionPostgresTest {

    private static final String CONTRASENA = "una-clave-larga";
    private static final String COOKIE = "refresh";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    @MockitoSpyBean
    private HasheadorDeContrasena hasheador;

    // Requirement: Login con credenciales válidas
    @Test
    void loginExitosoDevuelveAccessTokenYCookieDeRefresh() throws Exception {
        String email = registrar();

        ResponseEntity<String> respuesta = login(email, CONTRASENA);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode cuerpo = json.readTree(respuesta.getBody());
        assertThat(cuerpo.path("expiraEn").asLong()).isEqualTo(900);
        assertThat(get("/api/v1/pruebas/privado", cuerpo.path("accessToken").asText()).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(refreshDe(respuesta)).isNotBlank();
    }

    // Requirement: Cookie de refresh protegida
    @Test
    void laCookieDeRefreshTieneTodosSusAtributos() {
        ResponseEntity<String> respuesta = login(registrar(), CONTRASENA);

        String cookie = respuesta.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(cookie).startsWith(COOKIE + "=")
                .contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api/v1/auth", "Max-Age=1209600");
    }

    @Test
    void laBaseGuardaSoloElHashDelRefresh() {
        String email = registrar();
        String refresh = refreshDe(login(email, CONTRASENA));

        List<String> hashes = jdbc.queryForList("""
                SELECT r.token_hash FROM refresh_token r JOIN usuario u ON u.id = r.usuario_id WHERE u.email = ?""",
                String.class, email);

        assertThat(hashes).containsExactly(HashDeToken.sha256(refresh)).doesNotContain(refresh);
        assertThat(refresh.length()).isGreaterThanOrEqualTo(43);
    }

    // Requirement: Fallo de login genérico
    @Test
    void contrasenaIncorrectaYEmailInexistenteRespondenIgual() throws Exception {
        String email = registrar();

        ResponseEntity<String> malaClave = login(email, "otra-clave-larga");
        clearInvocations(hasheador);
        ResponseEntity<String> sinCuenta = login("nadie-" + sufijo() + "@example.com", CONTRASENA);

        assertThat(malaClave.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(sinCuenta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(sinInstance(malaClave)).isEqualTo(sinInstance(sinCuenta));
        assertThat(sinInstance(sinCuenta).path("codigo").asText()).isEqualTo("CREDENCIALES_INVALIDAS");
        // Sin cuenta igual se compara contra un hash ficticio, para no revelarlo por el tiempo de respuesta.
        verify(hasheador, times(1)).coincide(anyString(), anyString());
        assertThat(sinCuenta.getHeaders().get(HttpHeaders.SET_COOKIE)).isNull();
    }

    // Requirement: Rotación del refresh
    @Test
    void refreshValidoRotaElToken() throws Exception {
        String email = registrar();
        String refresh = refreshDe(login(email, CONTRASENA));

        ResponseEntity<String> respuesta = refrescar(refresh);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        String nuevo = refreshDe(respuesta);
        assertThat(nuevo).isNotEqualTo(refresh);
        assertThat(get("/api/v1/pruebas/privado", json.readTree(respuesta.getBody()).path("accessToken").asText())
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT revocado_at IS NOT NULL FROM refresh_token WHERE token_hash = ?",
                Boolean.class, HashDeToken.sha256(refresh))).isTrue();
    }

    @Test
    void refreshDesconocidoVencidoOAusenteExpiraLaSesion() throws Exception {
        String email = registrar();
        String vencido = refreshDe(login(email, CONTRASENA));
        jdbc.update("UPDATE refresh_token SET expira_at = now() - interval '1 minute' WHERE token_hash = ?",
                HashDeToken.sha256(vencido));

        for (ResponseEntity<String> respuesta : List.of(refrescar("desconocido-" + sufijo()), refrescar(vencido),
                http.exchange("/api/v1/auth/refresh", HttpMethod.POST, new HttpEntity<>(origenPropio()), String.class))) {
            assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(json.readTree(respuesta.getBody()).path("codigo").asText()).isEqualTo("SESION_EXPIRADA");
            assertThat(respuesta.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains(COOKIE + "=", "Max-Age=0");
        }
    }

    // Requirement: Detección de reuso del refresh
    @Test
    void reusarUnRefreshRotadoCierraTodasLasSesiones() throws Exception {
        String email = registrar();
        String dispositivoUno = refreshDe(login(email, CONTRASENA));
        String dispositivoDos = refreshDe(login(email, CONTRASENA));
        refrescar(dispositivoUno);

        ResponseEntity<String> reuso = refrescar(dispositivoUno);

        assertThat(reuso.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(json.readTree(reuso.getBody()).path("codigo").asText()).isEqualTo("SESION_EXPIRADA");
        assertThat(refrescar(dispositivoDos).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM refresh_token r JOIN usuario u ON u.id = r.usuario_id
                WHERE u.email = ? AND r.revocado_at IS NULL""", Integer.class, email)).isZero();
    }

    @Test
    void dosRefreshSimultaneosDelMismoTokenNoEmitenDosSesionesValidas() throws Exception {
        String refresh = refreshDe(login(registrar(), CONTRASENA));

        List<ResponseEntity<String>> respuestas = enParalelo(() -> refrescar(refresh), () -> refrescar(refresh));

        assertThat(respuestas).extracting(ResponseEntity::getStatusCode)
                .containsExactlyInAnyOrder(HttpStatus.OK, HttpStatus.UNAUTHORIZED);
    }

    // Requirement: Logout
    @Test
    void logoutRevocaElRefreshYBorraLaCookie() {
        String refresh = refreshDe(login(registrar(), CONTRASENA));

        ResponseEntity<String> respuesta = http.exchange("/api/v1/auth/logout", HttpMethod.POST,
                new HttpEntity<>(conCookie(refresh)), String.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(respuesta.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains(COOKIE + "=", "Max-Age=0");
        assertThat(refrescar(refresh).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void logoutSinCookie() {
        ResponseEntity<String> respuesta = http.exchange("/api/v1/auth/logout", HttpMethod.POST,
                new HttpEntity<>(origenPropio()), String.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    // Requirement: Refresh y logout solo desde el frontend propio (tarea 4.5)
    @Test
    void refreshDesdeOtroSitioSeRechazaSinTocarElToken() throws Exception {
        String refresh = refreshDe(login(registrar(), CONTRASENA));
        HttpHeaders cabeceras = conCookie(refresh);
        cabeceras.setOrigin("https://otro.example.com");

        ResponseEntity<String> respuesta = http.exchange("/api/v1/auth/refresh", HttpMethod.POST,
                new HttpEntity<>(cabeceras), String.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(json.readTree(respuesta.getBody()).path("codigo").asText()).isEqualTo("ORIGEN_NO_PERMITIDO");
        assertThat(refrescar(refresh).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void logoutDesdeOtroSitioSeRechaza() {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setOrigin("https://otro.example.com");

        assertThat(http.exchange("/api/v1/auth/logout", HttpMethod.POST, new HttpEntity<>(cabeceras), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void refreshDesdeElProxyDeViteFunciona() {
        // A través del proxy de Vite el navegador manda el origen del frontend, igual a APP_FRONTEND_ORIGIN.
        String refresh = refreshDe(login(registrar(), CONTRASENA));

        assertThat(refrescar(refresh).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private String registrar() {
        String email = "sesion-" + sufijo() + "@example.com";
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> respuesta = http.postForEntity("/api/v1/auth/registro", new HttpEntity<>(Map.of(
                "nombre", "Ana", "apellido", "Prueba", "email", email, "password", CONTRASENA,
                "nombreConsultorio", "Consultorio Sesion " + sufijo(), "aceptaTerminos", true), cabeceras), String.class);
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return email;
    }

    private ResponseEntity<String> login(String email, String password) {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setContentType(MediaType.APPLICATION_JSON);
        return http.postForEntity("/api/v1/auth/login",
                new HttpEntity<>(Map.of("email", email, "password", password), cabeceras), String.class);
    }

    private ResponseEntity<String> refrescar(String refresh) {
        return http.exchange("/api/v1/auth/refresh", HttpMethod.POST, new HttpEntity<>(conCookie(refresh)), String.class);
    }

    private ResponseEntity<String> get(String ruta, String accessToken) {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setBearerAuth(accessToken);
        return http.exchange(ruta, HttpMethod.GET, new HttpEntity<>(cabeceras), String.class);
    }

    private static HttpHeaders conCookie(String refresh) {
        HttpHeaders cabeceras = origenPropio();
        cabeceras.add(HttpHeaders.COOKIE, COOKIE + "=" + refresh);
        return cabeceras;
    }

    private static HttpHeaders origenPropio() {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setOrigin(ORIGEN_FRONTEND);
        return cabeceras;
    }

    private static String refreshDe(ResponseEntity<String> respuesta) {
        String cookie = respuesta.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(cookie).as("Set-Cookie de refresh").isNotNull();
        return cookie.substring((COOKIE + "=").length(), cookie.indexOf(';'));
    }

    private JsonNode sinInstance(ResponseEntity<String> respuesta) throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode nodo =
                (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(respuesta.getBody());
        nodo.remove("instance");
        return nodo;
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

    private static String sufijo() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
