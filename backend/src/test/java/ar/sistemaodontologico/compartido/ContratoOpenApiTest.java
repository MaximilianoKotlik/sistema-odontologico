package ar.sistemaodontologico.compartido;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Spec plataforma/contrato-api — Requirements: Contrato OpenAPI publicado; Swagger UI solo en local.
 * Además exporta el contrato a {@code build/openapi/openapi.json}, que el CI usa para verificar
 * que {@code frontend/src/shared/api-types.ts} está al día (design D7).
 */
class ContratoOpenApiTest extends IntegracionPostgresTest {

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private ObjectMapper json;

    @Test
    void publicaUnDocumentoOpenApi3() throws Exception {
        ResponseEntity<String> respuesta = http.getForEntity("/v3/api-docs", String.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode contrato = json.readTree(respuesta.getBody());
        assertThat(contrato.path("openapi").asText()).startsWith("3.");
        assertThat(contrato.path("info").path("title").asText()).isEqualTo("API Agenda Odontológica");
    }

    @Test
    void soloDescribeLaApiDeNegocio() throws Exception {
        JsonNode contrato = json.readTree(http.getForObject("/v3/api-docs", String.class));

        contrato.path("paths").fieldNames().forEachRemaining(ruta -> assertThat(ruta).startsWith("/api/v1/"));
    }

    @Test
    void exportaElContratoParaElFrontend() throws Exception {
        String destino = System.getProperty("openapi.output");
        assertThat(destino).as("el build define la propiedad openapi.output").isNotBlank();

        Path archivo = Path.of(destino);
        Files.createDirectories(archivo.getParent());
        ObjectNode contrato = (ObjectNode) json.readTree(http.getForObject("/v3/api-docs", String.class));
        contrato.remove("servers"); // el puerto aleatorio del test haría el archivo no determinista
        Files.writeString(archivo, json.writerWithDefaultPrettyPrinter().writeValueAsString(contrato) + "\n");

        assertThat(json.readTree(archivo.toFile()).path("openapi").asText()).startsWith("3.");
    }

    @Test
    void swaggerUiNoEstaDisponibleFueraDelPerfilLocal() {
        assertThat(http.getForEntity("/swagger-ui.html", String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
