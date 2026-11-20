package ar.sistemaodontologico.compartido;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Spec plataforma/entorno-local — Requirement: Estado de salud del backend. */
class SaludDelBackendTest extends IntegracionPostgresTest {

    @Autowired
    private TestRestTemplate http;

    @Test
    void conLaBaseDisponibleRespondeUp() {
        ResponseEntity<String> respuesta = http.getForEntity("/actuator/health", String.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).isEqualToIgnoringWhitespace("{\"status\":\"UP\"}");
    }

    @Test
    void noExponeDetallesInternos() {
        String cuerpo = http.getForObject("/actuator/health", String.class);

        assertThat(cuerpo).doesNotContain("components", "details", "jdbc", "postgres", APP_USER, "version");
    }
}
