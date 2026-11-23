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
 * C-23, spec seguridad-http "IP real detrás del proxy" (tarea 1.2, design D5). Con
 * {@code FORWARD_HEADERS_STRATEGY=native}, como en el VPS, la IP es la que agrega el proxy de confianza: Tomcat toma
 * la de más a la derecha que no sea un proxy, así lo que manda el cliente a la izquierda se ignora.
 */
@TestPropertySource(properties = {"FORWARD_HEADERS_STRATEGY=native", "app.rate-limit.login.capacidad=2"})
class IpDetrasDelProxyTest extends IntegracionPostgresTest {

    @Autowired
    private TestRestTemplate http;

    // Scenario: Dos personas detrás del proxy no comparten cupo
    @Test
    void dosIpsDetrasDelProxyTienenCuposSeparados() {
        assertThat(login("198.51.100.10")).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(login("198.51.100.10")).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(login("198.51.100.10")).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        assertThat(login("198.51.100.11")).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // Scenario: Un X-Forwarded-For falseado no cambia el cupo
    @Test
    void unXForwardedForQueMandaElClienteNoCambiaElCupo() {
        assertThat(login("192.0.2.1, 198.51.100.20")).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(login("192.0.2.2, 198.51.100.20")).isEqualTo(HttpStatus.UNAUTHORIZED);

        assertThat(login("192.0.2.3, 198.51.100.20")).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    private HttpStatusCode login(String xForwardedFor) {
        return LoginDesdeProxy.login(http, ORIGEN_FRONTEND, xForwardedFor);
    }
}
