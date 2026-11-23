package ar.sistemaodontologico.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import ar.sistemaodontologico.compartido.IntegracionPostgresTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.context.TestPropertySource;
import pruebas.seguridad.LoginDesdeProxy;

/**
 * C-23, spec seguridad-http "IP real detrás del proxy" (tarea 1.1, design D5). Con la estrategia por defecto
 * ({@code none}) {@code X-Forwarded-For} se ignora: nadie puede falsear su IP. Servidor real, porque la válvula de
 * Tomcat no pasa por MockMvc.
 */
@TestPropertySource(properties = "app.rate-limit.login.capacidad=3")
class IpSinProxyConfiguradoTest extends IntegracionPostgresTest {

    @Autowired
    private TestRestTemplate http;

    // Scenario: Sin proxy configurado, la cabecera no cambia la IP
    @Test
    void loginsConDistintosXForwardedForCompartenElCupoDeLaConexion() {
        assertThat(login("198.51.100.1")).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(login("198.51.100.2")).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(login("198.51.100.3")).isEqualTo(HttpStatus.UNAUTHORIZED);

        assertThat(login("198.51.100.4")).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    private HttpStatusCode login(String xForwardedFor) {
        return LoginDesdeProxy.login(http, ORIGEN_FRONTEND, xForwardedFor);
    }
}
