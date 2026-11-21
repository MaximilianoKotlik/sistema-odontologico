package ar.sistemaodontologico.agenda.web;

import static org.assertj.core.api.Assertions.assertThat;

import ar.sistemaodontologico.compartido.IntegracionPostgresTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;

/**
 * Las 13 operaciones de C-12 están en el contrato OpenAPI que consumen C-13 y C-14 (C-12, tarea 5.1). El
 * {@code api-types.ts} del frontend se regenera desde este contrato.
 */
class ContratoDeAgendaTest extends IntegracionPostgresTest {

    private static final List<String[]> OPERACIONES = List.of(
            new String[] {"/api/v1/agenda", "get"},
            new String[] {"/api/v1/turnos", "post"},
            new String[] {"/api/v1/turnos/{id}", "get"},
            new String[] {"/api/v1/turnos/{id}/estado", "patch"},
            new String[] {"/api/v1/turnos/{id}/reprogramar", "post"},
            new String[] {"/api/v1/turnos/{id}/duracion", "patch"},
            new String[] {"/api/v1/turnos/{id}/nota", "patch"},
            new String[] {"/api/v1/turnos/{id}/visto", "post"},
            new String[] {"/api/v1/turnos/fuera-de-disponibilidad", "get"},
            new String[] {"/api/v1/bloqueos", "get"},
            new String[] {"/api/v1/bloqueos", "post"},
            new String[] {"/api/v1/bloqueos/{id}", "delete"},
            new String[] {"/api/v1/pacientes", "get"});

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private ObjectMapper json;

    @Test
    void lasTreceOperacionesDeLaAgenda() throws Exception {
        JsonNode rutas = json.readTree(http.getForObject("/v3/api-docs", String.class)).path("paths");

        for (String[] operacion : OPERACIONES) {
            assertThat(rutas.path(operacion[0]).has(operacion[1])).as(operacion[1] + " " + operacion[0]).isTrue();
        }
    }

    @Test
    void laAgendaNoDeclaraDatosPersonalesDeMas() throws Exception {
        JsonNode esquemas = json.readTree(http.getForObject("/v3/api-docs", String.class))
                .path("components").path("schemas");

        assertThat(esquemas.path("TurnoResponse").path("properties").has("novedadPacienteAt")).isTrue();
        assertThat(esquemas.path("PacienteResumen").path("properties").fieldNames())
                .toIterable().containsExactlyInAnyOrder("id", "nombre", "apellido");
    }
}
