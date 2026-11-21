package ar.sistemaodontologico.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static pruebas.seguridad.ClienteDeAuth.CONTRASENA;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import pruebas.seguridad.ClienteDeAuth;

/** C-05, spec sesion-odontologo: bloqueo tras 5 fallos y auditoría de logins (tareas 1.2 a 1.5). */
class BloqueoDeLoginTest extends IntegracionDeAuthTest {

    private static final String INCORRECTA = "otra-clave-larga";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    // Scenario: Sexto intento con la contraseña correcta
    @Test
    void elSextoIntentoConLaContrasenaCorrectaRespondeCuentaBloqueada() throws Exception {
        String email = auth.registrar("bloqueo");
        for (int i = 0; i < 5; i++) {
            assertThat(auth.login(email, INCORRECTA).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        ResponseEntity<String> sexto = auth.login(email, CONTRASENA);

        assertThat(sexto.getStatusCode().value()).isEqualTo(423);
        JsonNode cuerpo = json.readTree(sexto.getBody());
        assertThat(cuerpo.path("codigo").asText()).isEqualTo("CUENTA_BLOQUEADA");
        assertThat(cuerpo.path("minutosRestantes").asLong()).isEqualTo(15);
        assertThat(cuerpo.path("detail").asText()).doesNotContain(email);
        assertThat(cuerpo.has("accessToken")).isFalse();
        assertThat(sexto.getHeaders().get(HttpHeaders.SET_COOKIE)).isNull();
    }

    // Scenario: Fin del bloqueo
    @Test
    void alTerminarLosQuinceMinutosElLoginVuelveAFuncionar() {
        String email = bloquear(auth.registrar("fin-bloqueo"));

        reloj.avanzar(Duration.ofMinutes(15));

        assertThat(auth.login(email, CONTRASENA).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // Scenario: Intentos durante el bloqueo no lo extienden
    @Test
    void losIntentosDuranteElBloqueoNoLoExtienden() throws Exception {
        String email = bloquear(auth.registrar("sin-extension"));

        reloj.avanzar(Duration.ofMinutes(10));
        ResponseEntity<String> durante = auth.login(email, INCORRECTA);
        assertThat(durante.getStatusCode().value()).isEqualTo(423);
        assertThat(json.readTree(durante.getBody()).path("minutosRestantes").asLong()).isEqualTo(5);

        reloj.avanzar(Duration.ofMinutes(5));
        assertThat(auth.login(email, CONTRASENA).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // Scenario: Fallos no consecutivos
    @Test
    void unLoginExitosoReiniciaElContador() {
        String email = auth.registrar("no-consecutivos");
        fallar(email, 4);
        assertThat(auth.login(email, CONTRASENA).getStatusCode()).isEqualTo(HttpStatus.OK);
        fallar(email, 4);

        assertThat(auth.login(email, CONTRASENA).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // Scenario: Login fallido auditado
    @Test
    void elLoginFallidoQuedaAuditadoAunqueLaRespuestaSea401() {
        String email = auth.registrar("auditoria-fallo");

        assertThat(auth.login(email, INCORRECTA).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        List<Map<String, Object>> eventos = eventos(email, "LOGIN_FALLIDO");
        assertThat(eventos).hasSize(1);
        Map<String, Object> evento = eventos.get(0);
        assertThat(evento.get("actor_tipo")).isEqualTo("ODONTOLOGO");
        assertThat(evento.get("actor_id")).isNull();
        assertThat(evento.get("entidad")).isEqualTo("usuario");
        assertThat(evento.get("ip")).isNotNull();
        assertThat(String.valueOf(evento.get("valor_nuevo"))).contains("\"intentos_fallidos\": 1");
    }

    @Test
    void elLoginDuranteElBloqueoTambienQuedaAuditadoComoFallido() {
        String email = bloquear(auth.registrar("auditoria-bloqueo"));

        assertThat(auth.login(email, CONTRASENA).getStatusCode().value()).isEqualTo(423);

        assertThat(eventos(email, "LOGIN_FALLIDO")).hasSize(6);
    }

    // Scenario: Login exitoso auditado
    @Test
    void elLoginExitosoQuedaAuditadoSinTokens() throws Exception {
        String email = auth.registrar("auditoria-exito");

        ResponseEntity<String> respuesta = auth.login(email, CONTRASENA);

        String access = json.readTree(respuesta.getBody()).path("accessToken").asText();
        String refresh = ClienteDeAuth.refreshDe(respuesta);
        List<Map<String, Object>> eventos = eventos(email, "LOGIN_EXITOSO");
        assertThat(eventos).hasSize(1);
        assertThat(eventos.get(0).get("actor_id")).isEqualTo(usuarioId(email));
        assertThat(eventos.toString()).doesNotContain(access).doesNotContain(refresh).doesNotContain(CONTRASENA);
    }

    // Scenario: Email inexistente
    @Test
    void unEmailSinCuentaNoGeneraEventos() {
        long antes = cantidadDeEventosDeLogin();

        assertThat(auth.login("nadie-bloqueo@example.com", CONTRASENA).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        assertThat(cantidadDeEventosDeLogin()).isEqualTo(antes);
    }

    // El email es CITEXT (C-04): el login no distingue mayúsculas, tampoco con el lock de fila de C-05.
    @Test
    void elLoginNoDistingueMayusculasEnElEmail() {
        String email = auth.registrar("mayusculas");

        assertThat(auth.login(email.toUpperCase(), CONTRASENA).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // Tarea 1.5: el lock de fila no pierde incrementos
    @Test
    void cincoFallosSimultaneosBloqueanLaCuenta() throws Exception {
        String email = auth.registrar("concurrencia");
        ExecutorService hilos = Executors.newFixedThreadPool(5);
        try {
            List<Callable<ResponseEntity<String>>> intentos = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                intentos.add(() -> auth.login(email, INCORRECTA));
            }
            for (Future<ResponseEntity<String>> intento : hilos.invokeAll(intentos)) {
                assertThat(intento.get().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            }
        } finally {
            hilos.shutdown();
        }

        assertThat(auth.login(email, CONTRASENA).getStatusCode().value()).isEqualTo(423);
    }

    private String bloquear(String email) {
        fallar(email, 5);
        return email;
    }

    private void fallar(String email, int veces) {
        for (int i = 0; i < veces; i++) {
            assertThat(auth.login(email, INCORRECTA).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    private Object usuarioId(String email) {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", Object.class, email);
    }

    private List<Map<String, Object>> eventos(String email, String accion) {
        return jdbc.queryForList("""
                SELECT e.actor_tipo, e.actor_id, e.entidad, e.entidad_id, host(e.ip) AS ip,
                       e.valor_anterior::text AS valor_anterior, e.valor_nuevo::text AS valor_nuevo
                FROM evento_auditoria e JOIN usuario u ON u.consultorio_id = e.consultorio_id AND u.id = e.entidad_id
                WHERE u.email = ? AND e.accion = ? ORDER BY e.ocurrido_at, e.id""", email, accion);
    }

    private long cantidadDeEventosDeLogin() {
        return jdbc.queryForObject("SELECT count(*) FROM evento_auditoria WHERE accion LIKE 'LOGIN_%'", Long.class);
    }
}
