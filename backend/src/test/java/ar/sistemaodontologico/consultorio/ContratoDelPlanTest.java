package ar.sistemaodontologico.consultorio;

import static org.assertj.core.api.Assertions.assertThat;

import ar.sistemaodontologico.compartido.IntegracionPostgresTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;

/** El 403 {@code PLAN_VENCIDO} en el contrato OpenAPI (C-20, tarea 3.4; design D8). */
class ContratoDelPlanTest extends IntegracionPostgresTest {

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private ObjectMapper json;

    private JsonNode rutas;

    @BeforeEach
    void leerContrato() throws Exception {
        rutas = json.readTree(http.getForObject("/v3/api-docs", String.class)).path("paths");
    }

    @Test
    void lasEscriturasBloqueadasDocumentanEl403() {
        assertThat(respuesta403("/api/v1/turnos", "post")).contains("PLAN_VENCIDO");
        assertThat(respuesta403("/api/v1/turnos/{id}/estado", "patch")).contains("PLAN_VENCIDO");
        assertThat(respuesta403("/api/v1/bloqueos/{id}", "delete")).contains("PLAN_VENCIDO");
        assertThat(respuesta403("/api/v1/consultorio", "put")).contains("PLAN_VENCIDO");
    }

    @Test
    void lasLecturasYLasPermitidasNo() {
        assertThat(respuesta403("/api/v1/turnos/{id}/visto", "post")).isEmpty();
        assertThat(respuesta403("/api/v1/auth/login", "post")).isEmpty();
        assertThat(respuesta403("/api/v1/consultorio", "get")).isEmpty();
        assertThat(respuesta403("/api/v1/public/consultorios/{slug}/turnos", "post")).isEmpty();
    }

    @Test
    void laConsultaDelPlanEstaEnElContrato() {
        JsonNode esquema = rutas.path("/api/v1/plan").path("get").path("responses").path("200");

        assertThat(esquema.isMissingNode()).isFalse();
    }

    private String respuesta403(String ruta, String metodo) {
        JsonNode operacion = rutas.path(ruta).path(metodo);
        assertThat(operacion.isMissingNode()).as(metodo + " " + ruta).isFalse();
        return operacion.path("responses").path("403").path("description").asText("");
    }
}
