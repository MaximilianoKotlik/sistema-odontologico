package ar.sistemaodontologico.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static pruebas.seguridad.ClienteDeAuth.CONTRASENA;

import ar.sistemaodontologico.auth.application.EnviadorDeEmails;
import ar.sistemaodontologico.auth.application.EnvioDeEmailFallido;
import ar.sistemaodontologico.auth.domain.HashDeToken;
import ar.sistemaodontologico.compartido.IntegracionPostgresTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pruebas.reloj.ConfiguracionDeRelojDePrueba;
import pruebas.reloj.RelojDePrueba;
import pruebas.seguridad.ClienteDeAuth;
import pruebas.seguridad.ControladorPrivadoDePrueba;

/** C-05, spec recuperacion-contrasena (tareas 3.3 a 3.7). El envío real por SMTP lo prueba EnviadorDeEmailsSmtpTest. */
@Import({ConfiguracionDeRelojDePrueba.class, ControladorPrivadoDePrueba.class})
@ExtendWith(OutputCaptureExtension.class)
class RecuperacionDeContrasenaTest extends IntegracionPostgresTest {

    private static final String NUEVA = "otra-clave-nueva";
    private static final String SOLICITUD = "/api/v1/auth/recuperacion";
    private static final String CONFIRMACION = "/api/v1/auth/recuperacion/confirmar";
    private static final Duration ESPERA_DEL_EMAIL = Duration.ofSeconds(5);

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private RelojDePrueba reloj;

    @MockitoBean
    private EnviadorDeEmails enviador;

    private ClienteDeAuth auth;

    @BeforeEach
    void cliente() {
        auth = new ClienteDeAuth(http, ORIGEN_FRONTEND);
    }

    // Scenario: Email registrado
    @Test
    void conUnEmailRegistradoResponde202YEnviaElLink() {
        String email = auth.registrar("recupero");

        ResponseEntity<String> respuesta = solicitar(email);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(enviador, timeout(ESPERA_DEL_EMAIL.toMillis())).enviarRecuperacion(eq(email), anyString());
    }

    // Scenario: Email no registrado
    @Test
    void conUnEmailSinCuentaRespondeIgualYNoEnviaNada() {
        String email = auth.registrar("recupero-comparado");
        ResponseEntity<String> conCuenta = solicitar(email);

        ResponseEntity<String> sinCuenta = solicitar("nadie-recupero@example.com");

        assertThat(sinCuenta.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(sinCuenta.getBody()).isEqualTo(conCuenta.getBody());
        verify(enviador, after(500).never()).enviarRecuperacion(eq("nadie-recupero@example.com"), anyString());
    }

    // Scenario: Token guardado como hash
    @Test
    void laBaseGuardaSoloElHashDelToken() {
        String email = auth.registrar("recupero-hash");

        String token = tokenEnviado(email);

        List<String> hashes = jdbc.queryForList("""
                SELECT t.token_hash FROM token_recuperacion t
                JOIN usuario u ON u.consultorio_id = t.consultorio_id AND u.id = t.usuario_id WHERE u.email = ?""",
                String.class, email);
        assertThat(hashes).containsExactly(HashDeToken.sha256(token)).doesNotContain(token);
        assertThat(token.length()).isGreaterThanOrEqualTo(43);
    }

    // Scenario: Nueva solicitud invalida la anterior
    @Test
    void unaNuevaSolicitudInvalidaElTokenAnterior() throws Exception {
        String email = auth.registrar("recupero-doble");
        String primero = tokenEnviado(email);
        String segundo = tokenEnviado(email);

        ResponseEntity<String> conElPrimero = confirmar(primero, NUEVA);

        assertThat(conElPrimero.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(codigo(conElPrimero)).isEqualTo("TOKEN_INVALIDO");
        assertThat(confirmar(segundo, NUEVA).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    // Scenario: Falla el servidor de correo
    @Test
    void siFallaElCorreoRespondeIgual202YElLogNoTieneElTokenNiElEmail(CapturedOutput salida) throws Exception {
        String email = auth.registrar("recupero-smtp");
        doThrow(new EnvioDeEmailFallido("El servidor de correo rechazó el email (MailSendException)"))
                .when(enviador).enviarRecuperacion(eq(email), anyString());

        assertThat(solicitar(email).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(enviador, timeout(ESPERA_DEL_EMAIL.toMillis())).enviarRecuperacion(eq(email), token.capture());
        esperarHasta(() -> salida.getOut().contains("No se pudo enviar el email de recuperación"));
        assertThat(salida.getOut()).contains("WARN").doesNotContain(token.getValue()).doesNotContain(email);
    }

    // Scenario: Restablecimiento exitoso
    @Test
    void restablecerCambiaLaContrasenaYCierraTodasLasSesiones() throws Exception {
        String email = auth.registrar("restablece");
        ResponseEntity<String> sesionUno = auth.login(email, CONTRASENA);
        ResponseEntity<String> sesionDos = auth.login(email, CONTRASENA);
        String access = json.readTree(sesionUno.getBody()).path("accessToken").asText();

        assertThat(confirmar(tokenEnviado(email), NUEVA).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(auth.login(email, NUEVA).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(auth.login(email, CONTRASENA).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(auth.refrescar(ClienteDeAuth.refreshDe(sesionUno)).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(auth.refrescar(ClienteDeAuth.refreshDe(sesionDos)).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        ResponseEntity<String> conAccessViejo = auth.getConSesion("/api/v1/pruebas/privado", access);
        assertThat(conAccessViejo.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(codigo(conAccessViejo)).isEqualTo("NO_AUTENTICADO");
    }

    // Scenario: Restablecer desbloquea la cuenta
    @Test
    void restablecerDesbloqueaLaCuenta() {
        String email = auth.registrar("restablece-bloqueada");
        for (int i = 0; i < 5; i++) {
            auth.login(email, "una-clave-equivocada");
        }
        assertThat(auth.login(email, CONTRASENA).getStatusCode().value()).isEqualTo(423);

        assertThat(confirmar(tokenEnviado(email), NUEVA).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(auth.login(email, NUEVA).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // Requirement: Token inválido (vencido, ya usado y desconocido responden igual)
    @Test
    void unTokenVencidoUsadoODesconocidoRespondenIgualYNoCambianLaContrasena() throws Exception {
        String emailVencido = auth.registrar("token-vencido");
        String vencido = tokenEnviado(emailVencido);
        String emailUsado = auth.registrar("token-usado");
        String usado = tokenEnviado(emailUsado);
        assertThat(confirmar(usado, NUEVA).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        reloj.avanzar(Duration.ofMinutes(31));

        ResponseEntity<String> conVencido = confirmar(vencido, "clave-del-vencido");
        ResponseEntity<String> conUsado = confirmar(usado, "clave-del-usado");
        ResponseEntity<String> conDesconocido = confirmar("q7Zt_9xYv2LmN0pK-4sDfGhJ8wErTyUi1oPa3bCdE5f", NUEVA);

        for (ResponseEntity<String> respuesta : List.of(conVencido, conUsado, conDesconocido)) {
            assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(codigo(respuesta)).isEqualTo("TOKEN_INVALIDO");
        }
        assertThat(sinInstance(conVencido)).isEqualTo(sinInstance(conUsado)).isEqualTo(sinInstance(conDesconocido));
        assertThat(auth.login(emailVencido, CONTRASENA).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(auth.login(emailUsado, NUEVA).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void unTokenVacioONuloEsTokenInvalido() throws Exception {
        ResponseEntity<String> vacio = auth.post(CONFIRMACION, Map.of("token", "", "password", NUEVA));
        ResponseEntity<String> sinToken = auth.post(CONFIRMACION, Map.of("password", NUEVA));

        assertThat(codigo(vacio)).isEqualTo("TOKEN_INVALIDO");
        assertThat(codigo(sinToken)).isEqualTo("TOKEN_INVALIDO");
    }

    // Scenario: Contraseña nueva de 9 caracteres
    @Test
    void unaContrasenaDebilNoConsumeElToken() throws Exception {
        String email = auth.registrar("restablece-debil");
        String token = tokenEnviado(email);

        ResponseEntity<String> debil = confirmar(token, "123456789");

        assertThat(debil.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(codigo(debil)).isEqualTo("PASSWORD_DEBIL");
        assertThat(confirmar(token, NUEVA).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    // Scenario: Evento registrado sin secretos
    @Test
    void elRestablecimientoQuedaAuditadoSinSecretos() {
        String email = auth.registrar("restablece-auditado");
        String token = tokenEnviado(email);

        assertThat(confirmar(token, NUEVA).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        List<Map<String, Object>> eventos = jdbc.queryForList("""
                SELECT e.actor_tipo, e.actor_id, e.entidad_id, host(e.ip) AS ip, e.valor_nuevo::text AS valor_nuevo,
                       u.id AS usuario, u.password_hash
                FROM evento_auditoria e JOIN usuario u ON u.consultorio_id = e.consultorio_id AND u.id = e.entidad_id
                WHERE u.email = ? AND e.accion = 'PASSWORD_RECUPERADA'""", email);
        assertThat(eventos).hasSize(1);
        Map<String, Object> evento = eventos.get(0);
        assertThat(evento.get("actor_tipo")).isEqualTo("ODONTOLOGO");
        assertThat(evento.get("actor_id")).isEqualTo(evento.get("usuario"));
        assertThat(evento.get("ip")).isNotNull();
        assertThat((String) evento.get("valor_nuevo"))
                .contains("\"credenciales_version\": 2")
                .doesNotContain(token)
                .doesNotContain(HashDeToken.sha256(token))
                .doesNotContain(NUEVA)
                .doesNotContain((String) evento.get("password_hash"));
    }

    private ResponseEntity<String> solicitar(String email) {
        return auth.post(SOLICITUD, Map.of("email", email));
    }

    private ResponseEntity<String> confirmar(String token, String password) {
        return auth.post(CONFIRMACION, Map.of("token", token, "password", password));
    }

    /** Solicita la recuperación y espera el token de <em>esta</em> solicitud, que se envía después del commit. */
    private String tokenEnviado(String email) {
        int previos = tokensEnviados(email).size();
        assertThat(solicitar(email).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        long limite = System.nanoTime() + ESPERA_DEL_EMAIL.toNanos();
        while (tokensEnviados(email).size() <= previos) {
            if (System.nanoTime() > limite) {
                throw new AssertionError("No se envió el email de recuperación");
            }
            Thread.onSpinWait();
        }
        List<String> enviados = tokensEnviados(email);
        return enviados.get(enviados.size() - 1);
    }

    private List<String> tokensEnviados(String email) {
        return Mockito.mockingDetails(enviador).getInvocations().stream()
                .filter(llamada -> llamada.getMethod().getName().equals("enviarRecuperacion"))
                .filter(llamada -> email.equals(llamada.getArgument(0)))
                .map(llamada -> (String) llamada.getArgument(1))
                .toList();
    }

    private String codigo(ResponseEntity<String> respuesta) throws Exception {
        return json.readTree(respuesta.getBody()).path("codigo").asText();
    }

    private JsonNode sinInstance(ResponseEntity<String> respuesta) throws Exception {
        ObjectNode cuerpo = (ObjectNode) json.readTree(respuesta.getBody());
        cuerpo.remove("instance");
        cuerpo.remove("referencia");
        return cuerpo;
    }

    private static void esperarHasta(java.util.function.BooleanSupplier condicion) throws InterruptedException {
        long limite = System.nanoTime() + ESPERA_DEL_EMAIL.toNanos();
        while (!condicion.getAsBoolean() && System.nanoTime() < limite) {
            Thread.sleep(50);
        }
    }
}
