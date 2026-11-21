package ar.sistemaodontologico.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static pruebas.seguridad.ClienteDeAuth.CONTRASENA;

import ar.sistemaodontologico.auth.application.EnviadorDeEmails;
import ar.sistemaodontologico.compartido.IntegracionPostgresTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pruebas.reloj.ConfiguracionDeRelojDePrueba;
import pruebas.reloj.RelojDePrueba;
import pruebas.seguridad.ClienteDeAuth;
import pruebas.seguridad.ControladorPrivadoDePrueba;

/**
 * C-05, spec seguridad-http: límite de solicitudes en autenticación (tareas 5.2 y 5.3, design D8). Contexto propio
 * con límites bajos; cada test usa su propia IP de documentación (RFC 5737) para no compartir cupos.
 */
@AutoConfigureMockMvc
@Import({ConfiguracionDeRelojDePrueba.class, ControladorPrivadoDePrueba.class})
@TestPropertySource(properties = {
    "app.rate-limit.login.capacidad=2",
    "app.rate-limit.recuperacion.capacidad=3",
    "app.rate-limit.recuperacion-por-email.capacidad=2",
    "app.rate-limit.cambiar-password.capacidad=2"
})
class LimiteDeSolicitudesTest extends IntegracionPostgresTest {

    private static final AtomicInteger PROXIMA_IP = new AtomicInteger(1);

    @Autowired
    private MockMvc mvc;

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
    private String ip;

    @BeforeEach
    void preparar() {
        auth = new ClienteDeAuth(http, ORIGEN_FRONTEND);
        ip = "203.0.113." + PROXIMA_IP.getAndIncrement();
    }

    // Scenario: Ráfaga de logins desde una IP
    @Test
    void superadoElLimiteDeLoginsDeUnaIpResponde429SinContarElIntento() throws Exception {
        String email = auth.registrar("rafaga");
        login(email, "una-clave-equivocada", ip);
        login(email, "una-clave-equivocada", ip);

        MockHttpServletResponse respuesta = login(email, "una-clave-equivocada", ip);

        assertThat(respuesta.getStatus()).isEqualTo(429);
        assertThat(codigo(respuesta)).isEqualTo("DEMASIADAS_SOLICITUDES");
        assertThat(respuesta.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(Long.parseLong(respuesta.getHeader(HttpHeaders.RETRY_AFTER))).isEqualTo(30);
        assertThat(jdbc.queryForObject("SELECT intentos_fallidos FROM usuario WHERE email = ?", Integer.class, email))
                .isEqualTo(2);
    }

    @Test
    void otraIpNoComparteElCupo() throws Exception {
        String email = auth.registrar("otra-ip");
        login(email, CONTRASENA, ip);
        login(email, CONTRASENA, ip);
        assertThat(login(email, CONTRASENA, ip).getStatus()).isEqualTo(429);

        assertThat(login(email, CONTRASENA, "198.51.100." + PROXIMA_IP.getAndIncrement()).getStatus()).isEqualTo(200);
    }

    // Scenario: Recuperación del límite
    @Test
    void pasadoElTiempoDeRetryAfterLaIpVuelveAPoderHacerLogin() throws Exception {
        String email = auth.registrar("recupera-limite");
        login(email, CONTRASENA, ip);
        login(email, CONTRASENA, ip);
        MockHttpServletResponse rechazo = login(email, CONTRASENA, ip);
        assertThat(rechazo.getStatus()).isEqualTo(429);

        reloj.avanzar(Duration.ofSeconds(Long.parseLong(rechazo.getHeader(HttpHeaders.RETRY_AFTER))));

        assertThat(login(email, CONTRASENA, ip).getStatus()).isEqualTo(200);
    }

    // Scenario: Recuperación repetida para un mismo email (también para un email sin cuenta)
    @Test
    void superadoElLimiteDeRecuperacionesDeUnEmailResponde429AunDesdeOtraIpYNoEnviaOtroEmail() throws Exception {
        String email = auth.registrar("recupera-repetido");
        assertThat(recuperar(email, ip).getStatus()).isEqualTo(202);
        assertThat(recuperar(email.toUpperCase(), otraIp()).getStatus()).isEqualTo(202);
        verify(enviador, timeout(5000).times(2)).enviarRecuperacion(eq(email), anyString());

        MockHttpServletResponse tercera = recuperar(email, otraIp());

        assertThat(tercera.getStatus()).isEqualTo(429);
        assertThat(codigo(tercera)).isEqualTo("DEMASIADAS_SOLICITUDES");
        assertThat(tercera.getHeader(HttpHeaders.RETRY_AFTER)).isNotBlank();
        verify(enviador, after(500).times(2)).enviarRecuperacion(eq(email), anyString());
    }

    @Test
    void elLimitePorEmailCuentaIgualParaUnEmailSinCuenta() throws Exception {
        String email = "sin-cuenta-limite-" + PROXIMA_IP.getAndIncrement() + "@example.com";
        recuperar(email, ip);
        recuperar(email, otraIp());

        assertThat(recuperar(email, otraIp()).getStatus()).isEqualTo(429);
    }

    @Test
    void superadoElLimiteDeRecuperacionesDeUnaIpResponde429() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(recuperar("ip-" + i + "-" + ip + "@example.com", ip).getStatus()).isEqualTo(202);
        }

        assertThat(recuperar("ip-final-" + ip + "@example.com", ip).getStatus()).isEqualTo(429);
    }

    // Scenario: Cambio de contraseña repetido
    @Test
    void superadoElLimiteDeCambiosDeContrasenaDeUnUsuarioResponde429() throws Exception {
        String email = auth.registrar("cambio-repetido");
        String access = json.readTree(auth.login(email, CONTRASENA).getBody()).path("accessToken").asText();
        cambiar(access, "no-es-la-actual", otraIp());
        cambiar(access, "no-es-la-actual", otraIp());

        MockHttpServletResponse tercero = cambiar(access, CONTRASENA, otraIp());

        assertThat(tercero.getStatus()).isEqualTo(429);
        assertThat(codigo(tercero)).isEqualTo("DEMASIADAS_SOLICITUDES");
        assertThat(auth.login(email, CONTRASENA).getStatusCode().value()).isEqualTo(200);
    }

    // Scenario: Rutas fuera de autenticación
    @Test
    void elLimiteNoSeAplicaFueraDeAutenticacion() throws Exception {
        String email = auth.registrar("fuera-de-auth");
        String access = json.readTree(auth.login(email, CONTRASENA).getBody()).path("accessToken").asText();

        for (int i = 0; i < 10; i++) {
            assertThat(mvc.perform(desde(get("/api/v1/pruebas/privado")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + access), ip))
                    .andReturn().getResponse().getStatus()).isEqualTo(200);
        }
    }

    private MockHttpServletResponse login(String email, String password, String desdeIp) throws Exception {
        return enviar(post("/api/v1/auth/login"), Map.of("email", email, "password", password), desdeIp);
    }

    private MockHttpServletResponse recuperar(String email, String desdeIp) throws Exception {
        return enviar(post("/api/v1/auth/recuperacion"), Map.of("email", email), desdeIp);
    }

    private MockHttpServletResponse cambiar(String access, String actual, String desdeIp) throws Exception {
        return enviar(post("/api/v1/auth/cambiar-password").header(HttpHeaders.AUTHORIZATION, "Bearer " + access),
                Map.of("passwordActual", actual, "passwordNueva", "otra-clave-nueva"), desdeIp);
    }

    private MockHttpServletResponse enviar(MockHttpServletRequestBuilder pedido, Object cuerpo, String desdeIp)
            throws Exception {
        return mvc.perform(desde(pedido.contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(cuerpo)), desdeIp))
                .andReturn().getResponse();
    }

    private static MockHttpServletRequestBuilder desde(MockHttpServletRequestBuilder pedido, String desdeIp) {
        return pedido.with(peticion -> {
            peticion.setRemoteAddr(desdeIp);
            return peticion;
        });
    }

    private static String otraIp() {
        return "198.51.100." + PROXIMA_IP.getAndIncrement();
    }

    private String codigo(MockHttpServletResponse respuesta) throws Exception {
        JsonNode cuerpo = json.readTree(respuesta.getContentAsString());
        return cuerpo.path("codigo").asText();
    }
}
