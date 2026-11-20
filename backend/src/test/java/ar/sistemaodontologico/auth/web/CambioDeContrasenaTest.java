package ar.sistemaodontologico.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static pruebas.seguridad.ClienteDeAuth.CONTRASENA;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import pruebas.seguridad.ClienteDeAuth;

/** C-05, spec sesion-odontologo: cambio de contraseña con sesión iniciada (US-005, tareas 4.1 a 4.3). */
class CambioDeContrasenaTest extends IntegracionDeAuthTest {

    private static final String RUTA = "/api/v1/auth/cambiar-password";
    private static final String NUEVA = "otra-clave-nueva";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    // Scenario: Cambio exitoso
    @Test
    void cambiarLaContrasenaCierraTodasLasSesionesYBorraLaCookie() throws Exception {
        String email = auth.registrar("cambio");
        ResponseEntity<String> dispositivoUno = auth.login(email, CONTRASENA);
        ResponseEntity<String> dispositivoDos = auth.login(email, CONTRASENA);
        String access = accessDe(dispositivoUno);

        ResponseEntity<String> respuesta = cambiar(access, ClienteDeAuth.refreshDe(dispositivoUno), CONTRASENA, NUEVA);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(respuesta.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).startsWith("refresh=;")
                .contains("Max-Age=0", "Path=/api/v1/auth");
        assertThat(auth.getConSesion("/api/v1/pruebas/privado", access).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(auth.refrescar(ClienteDeAuth.refreshDe(dispositivoUno)).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(auth.refrescar(ClienteDeAuth.refreshDe(dispositivoDos)).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(auth.login(email, NUEVA).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(auth.login(email, CONTRASENA).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // Scenario: Contraseña actual incorrecta
    @Test
    void conLaContrasenaActualIncorrectaNoCambiaNada() throws Exception {
        String email = auth.registrar("cambio-actual-mal");
        String access = accessDe(auth.login(email, CONTRASENA));

        ResponseEntity<String> respuesta = cambiar(access, null, "no-es-la-actual", NUEVA);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(codigo(respuesta)).isEqualTo("PASSWORD_ACTUAL_INVALIDA");
        assertThat(auth.getConSesion("/api/v1/pruebas/privado", access).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(auth.login(email, CONTRASENA).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // Scenario: Contraseña nueva débil
    @Test
    void conUnaContrasenaNuevaDebilNoCambiaNada() throws Exception {
        String email = auth.registrar("cambio-debil");
        String access = accessDe(auth.login(email, CONTRASENA));

        for (String debil : List.of("123456789", email.toUpperCase())) {
            ResponseEntity<String> respuesta = cambiar(access, null, CONTRASENA, debil);
            assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(codigo(respuesta)).isEqualTo("PASSWORD_DEBIL");
        }
        assertThat(auth.login(email, CONTRASENA).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // Scenario: Sin sesión
    @Test
    void sinAccessTokenResponde401() throws Exception {
        ResponseEntity<String> respuesta = auth.post(RUTA, Map.of("passwordActual", CONTRASENA, "passwordNueva", NUEVA));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(codigo(respuesta)).isEqualTo("NO_AUTENTICADO");
    }

    // Tarea 4.2: una contraseña actual incorrecta no suma al contador de bloqueo del login
    @Test
    void unaContrasenaActualIncorrectaNoSumaAlBloqueoDelLogin() throws Exception {
        String email = auth.registrar("cambio-sin-bloqueo");
        String access = accessDe(auth.login(email, CONTRASENA));

        for (int i = 0; i < 3; i++) {
            assertThat(cambiar(access, null, "no-es-la-actual", NUEVA).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }

        assertThat(jdbc.queryForObject("SELECT intentos_fallidos FROM usuario WHERE email = ?", Integer.class, email))
                .isZero();
    }

    // Requirement: Auditoría del cambio de contraseña
    @Test
    void elCambioQuedaAuditadoSinSecretos() throws Exception {
        String email = auth.registrar("cambio-auditado");
        String hashAnterior = jdbc.queryForObject("SELECT password_hash FROM usuario WHERE email = ?", String.class,
                email);
        String access = accessDe(auth.login(email, CONTRASENA));

        assertThat(cambiar(access, null, CONTRASENA, NUEVA).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        List<Map<String, Object>> eventos = jdbc.queryForList("""
                SELECT e.actor_tipo, e.actor_id, host(e.ip) AS ip, e.valor_nuevo::text AS valor_nuevo,
                       u.id AS usuario, u.password_hash
                FROM evento_auditoria e JOIN usuario u ON u.consultorio_id = e.consultorio_id AND u.id = e.entidad_id
                WHERE u.email = ? AND e.accion = 'PASSWORD_CAMBIADA'""", email);
        assertThat(eventos).hasSize(1);
        Map<String, Object> evento = eventos.get(0);
        assertThat(evento.get("actor_tipo")).isEqualTo("ODONTOLOGO");
        assertThat(evento.get("actor_id")).isEqualTo(evento.get("usuario"));
        assertThat(evento.get("ip")).isNotNull();
        assertThat((String) evento.get("valor_nuevo"))
                .contains("\"credenciales_version\": 2")
                .doesNotContain(CONTRASENA).doesNotContain(NUEVA)
                .doesNotContain(hashAnterior).doesNotContain((String) evento.get("password_hash"));
    }

    private ResponseEntity<String> cambiar(String access, String refresh, String actual, String nueva) {
        return auth.postConSesion(RUTA, Map.of("passwordActual", actual, "passwordNueva", nueva), access, refresh);
    }

    private String accessDe(ResponseEntity<String> login) throws Exception {
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        return json.readTree(login.getBody()).path("accessToken").asText();
    }

    private String codigo(ResponseEntity<String> respuesta) throws Exception {
        return json.readTree(respuesta.getBody()).path("codigo").asText();
    }
}
