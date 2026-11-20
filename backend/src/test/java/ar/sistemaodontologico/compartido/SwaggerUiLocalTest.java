package ar.sistemaodontologico.compartido;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.ActiveProfiles;

/** Spec plataforma/contrato-api — Requirement: Swagger UI solo en desarrollo local (perfil local). */
@ActiveProfiles({"test", "local"})
class SwaggerUiLocalTest extends IntegracionPostgresTest {

    @Autowired
    private TestRestTemplate http;

    @Test
    void conElPerfilLocalMuestraSwaggerUi() {
        String pagina = http.getForObject("/swagger-ui/index.html", String.class);

        assertThat(http.getForEntity("/swagger-ui.html", String.class).getStatusCode().is4xxClientError()).isFalse();
        assertThat(pagina).containsIgnoringCase("swagger");
    }
}
