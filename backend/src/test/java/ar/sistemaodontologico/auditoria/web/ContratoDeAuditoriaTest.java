package ar.sistemaodontologico.auditoria.web;

import static org.assertj.core.api.Assertions.assertThat;

import ar.sistemaodontologico.compartido.IntegracionPostgresTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;

/** El registro no puede compartir schema con la respuesta del alta de cuenta (C-18, tarea 4.5). */
class ContratoDeAuditoriaTest extends IntegracionPostgresTest {

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private ObjectMapper json;

    @Test
    void elRegistroPublicaSusPropiosCampos() throws Exception {
        JsonNode contrato = json.readTree(http.getForObject("/v3/api-docs", String.class));
        String referencia = contrato.path("paths").path("/api/v1/auditoria").path("get").path("responses").path("200")
                .path("content").elements().next().path("schema").path("$ref").asText();
        JsonNode propiedades = contrato.at(referencia.substring(1)).path("properties");

        assertThat(propiedades.has("eventos")).isTrue();
        assertThat(propiedades.has("siguienteCursor")).isTrue();
        assertThat(propiedades.has("usuarioId")).isFalse();
    }
}
