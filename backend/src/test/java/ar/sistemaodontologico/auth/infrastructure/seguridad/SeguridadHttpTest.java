package ar.sistemaodontologico.auth.infrastructure.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import ar.sistemaodontologico.compartido.IntegracionPostgresTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import java.util.Base64;
import java.util.UUID;
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
import pruebas.seguridad.ControladorPrivadoDePrueba;
import pruebas.seguridad.CuentasDePrueba;
import pruebas.seguridad.CuentasDePrueba.Cuenta;
import pruebas.seguridad.TokensDePrueba;

/**
 * Spec seguridad-http — Requirement: Rutas privadas por defecto (tarea 5.1); spec sesion-odontologo —
 * Requirements: El consultorio activo sale de la sesión e Invalidación por versión de credenciales (tarea 5.2).
 */
@Import(ControladorPrivadoDePrueba.class)
class SeguridadHttpTest extends IntegracionPostgresTest {

    private static final String PRIVADA = "/api/v1/pruebas/privado";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    @Test
    void sinTokenRespondeNoAutenticadoEnProblemDetails() throws Exception {
        ResponseEntity<String> respuesta = http.getForEntity(PRIVADA, String.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(respuesta.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(json.readTree(respuesta.getBody()).path("codigo").asText()).isEqualTo("NO_AUTENTICADO");
        assertThat(respuesta.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
    }

    @Test
    void tokensInvalidosRespondenIgualSinExplicarLaCausa() throws Exception {
        Cuenta cuenta = CuentasDePrueba.crear(jdbc);
        String otraClave = Base64.getEncoder().encodeToString(new byte[48]);
        String[] invalidos = {
            TokensDePrueba.vencido(SECRETO_JWT_DE_PRUEBA, cuenta.usuario(), cuenta.consultorio()),
            TokensDePrueba.valido(otraClave, cuenta.usuario(), cuenta.consultorio(), 1),
            // Misma clave, otro algoritmo: tiene que rechazarse aunque la firma sea válida.
            TokensDePrueba.conAlgoritmo(SECRETO_JWT_DE_PRUEBA, JWSAlgorithm.HS384, cuenta.usuario(), cuenta.consultorio()),
            "esto.no-es.un-jwt"
        };
        String cuerpoDeReferencia = null;
        for (String token : invalidos) {
            ResponseEntity<String> respuesta = get(PRIVADA, token);

            assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            JsonNode cuerpo = json.readTree(respuesta.getBody());
            assertThat(cuerpo.path("codigo").asText()).isEqualTo("NO_AUTENTICADO");
            assertThat(respuesta.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
            cuerpoDeReferencia = cuerpoDeReferencia == null ? respuesta.getBody() : cuerpoDeReferencia;
            assertThat(respuesta.getBody()).isEqualTo(cuerpoDeReferencia);
        }
    }

    @Test
    void conTokenValidoElConsultorioActivoEsElDelToken() throws Exception {
        Cuenta cuenta = CuentasDePrueba.crear(jdbc);

        ResponseEntity<String> respuesta =
                get(PRIVADA, TokensDePrueba.valido(SECRETO_JWT_DE_PRUEBA, cuenta.usuario(), cuenta.consultorio(), 1));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(respuesta.getBody()).path("consultorio").asText())
                .isEqualTo(cuenta.consultorio().toString());
    }

    @Test
    void unaCabeceraConOtroConsultorioNoCambiaElTenant() throws Exception {
        Cuenta cuenta = CuentasDePrueba.crear(jdbc);
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setBearerAuth(TokensDePrueba.valido(SECRETO_JWT_DE_PRUEBA, cuenta.usuario(), cuenta.consultorio(), 1));
        cabeceras.set("X-Consultorio-Id", UUID.randomUUID().toString());

        ResponseEntity<String> respuesta = http.exchange(PRIVADA + "?consultorio_id=" + UUID.randomUUID(),
                HttpMethod.GET, new HttpEntity<>(cabeceras), String.class);

        assertThat(json.readTree(respuesta.getBody()).path("consultorio").asText())
                .isEqualTo(cuenta.consultorio().toString());
    }

    @Test
    void unTokenConVersionDeCredencialesVencidaSeRechaza() throws Exception {
        Cuenta cuenta = CuentasDePrueba.crear(jdbc);
        String tokenVersionUno = TokensDePrueba.valido(SECRETO_JWT_DE_PRUEBA, cuenta.usuario(), cuenta.consultorio(), 1);
        jdbc.update("UPDATE usuario SET credenciales_version = 2 WHERE id = ?", cuenta.usuario());

        ResponseEntity<String> respuesta = get(PRIVADA, tokenVersionUno);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(json.readTree(respuesta.getBody()).path("codigo").asText()).isEqualTo("NO_AUTENTICADO");
    }

    @Test
    void unTokenCuyoUsuarioNoEsDeEseConsultorioSeRechaza() {
        Cuenta cuentaA = CuentasDePrueba.crear(jdbc);
        Cuenta cuentaB = CuentasDePrueba.crear(jdbc);

        ResponseEntity<String> respuesta =
                get(PRIVADA, TokensDePrueba.valido(SECRETO_JWT_DE_PRUEBA, cuentaA.usuario(), cuentaB.consultorio(), 1));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void lasRutasPublicasNoPidenToken() throws Exception {
        assertThat(http.getForEntity("/actuator/health", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(http.getForEntity("/v3/api-docs", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        // El login procesa el pedido (y lo rechaza por credenciales), en lugar de cortarlo la seguridad.
        ResponseEntity<String> login = http.postForEntity("/api/v1/auth/login", jsonVacio(), String.class);
        assertThat(json.readTree(login.getBody()).path("codigo").asText()).isEqualTo("CREDENCIALES_INVALIDAS");
    }

    @Test
    void laApiPublicaDelPacienteNoPideToken() throws Exception {
        ResponseEntity<String> respuesta = http.getForEntity("/api/v1/public/no-existe-todavia", String.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(json.readTree(respuesta.getBody()).path("codigo").asText()).isEqualTo("RECURSO_NO_ENCONTRADO");
    }

    @Test
    void swaggerNoEstaDisponibleFueraDeLocal() {
        assertThat(http.getForEntity("/swagger-ui.html", String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private ResponseEntity<String> get(String ruta, String token) {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setBearerAuth(token);
        return http.exchange(ruta, HttpMethod.GET, new HttpEntity<>(cabeceras), String.class);
    }

    private static HttpEntity<String> jsonVacio() {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>("{}", cabeceras);
    }
}
