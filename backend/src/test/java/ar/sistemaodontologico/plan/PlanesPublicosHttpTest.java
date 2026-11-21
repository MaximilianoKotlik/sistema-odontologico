package ar.sistemaodontologico.plan;

import static org.assertj.core.api.Assertions.assertThat;

import ar.sistemaodontologico.compartido.IntegracionPostgresTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Spec public-plans — Requirement: Catálogo público sin autenticación, con la cadena de seguridad real (C-04) y el
 * contrato OpenAPI que consume el frontend. Tarea 2.2 y 4.1 (sin datos de consultorios ni de pacientes).
 */
class PlanesPublicosHttpTest extends IntegracionPostgresTest {

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private ObjectMapper json;

    @Test
    void unVisitanteSinTokenRecibeElCatalogo() throws Exception {
        ResponseEntity<String> respuesta = http.getForEntity("/api/v1/public/planes", String.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode cuerpo = json.readTree(respuesta.getBody());
        assertThat(cuerpo.path("planes").get(0).path("importeARS").asText()).isEqualTo("14900.00");
        assertThat(cuerpo.path("prueba").path("requiereMedioDePago").asBoolean()).isFalse();
        assertThat(cuerpo.path("actualizadoEn").asText()).isEqualTo("2026-11-20T03:00:00Z");
        assertThat(respuesta.getHeaders().get(HttpHeaders.SET_COOKIE)).isNull();
    }

    @Test
    void laRespuestaNoExponeDatosDeConsultoriosNiDePacientes() throws Exception {
        JsonNode cuerpo = json.readTree(http.getForObject("/api/v1/public/planes", String.class));

        // Solo los campos del contrato (design D3): ningún id de consultorio, email, teléfono ni dato de paciente.
        assertThat(cuerpo.properties()).extracting(java.util.Map.Entry::getKey)
                .containsExactlyInAnyOrder("planes", "prueba", "actualizadoEn");
        cuerpo.path("planes").forEach(plan -> assertThat(plan.properties()).extracting(java.util.Map.Entry::getKey)
                .isSubsetOf("id", "nombre", "descripcion", "incluye", "importeARS", "moneda", "impuestosIncluidos"));
        assertThat(cuerpo.path("prueba").properties()).extracting(java.util.Map.Entry::getKey)
                .containsExactlyInAnyOrder("dias", "requiereMedioDePago");
    }

    @Test
    void elContratoOpenApiDescribeElEndpointYSuEsquema() throws Exception {
        JsonNode contrato = json.readTree(http.getForObject("/v3/api-docs", String.class));

        assertThat(contrato.path("paths").has("/api/v1/public/planes")).isTrue();
        JsonNode esquemas = contrato.path("components").path("schemas");
        assertThat(esquemas.path("PlanesPublicosResponse").path("properties").has("actualizadoEn")).isTrue();
        assertThat(esquemas.path("PlanPublicoResponse").path("properties").path("importeARS").path("type").asText())
                .isEqualTo("string");
    }
}
