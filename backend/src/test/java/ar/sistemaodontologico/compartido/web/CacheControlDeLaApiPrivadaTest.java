package ar.sistemaodontologico.compartido.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.sistemaodontologico.consultorio.web.ApiDelPlanTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * C-23, spec seguridad-http "Respuestas privadas sin caché" (tarea 2.1, design D7). Las respuestas de la API privada
 * traen datos del consultorio y de sus pacientes: ni el navegador ni un proxy intermedio las guardan, tampoco los
 * errores. La descarga de la exportación lo verifica {@code ExportacionApiTest}.
 */
class CacheControlDeLaApiPrivadaTest extends ApiDelPlanTest {

    // Scenario: Lectura privada
    @Test
    void unaLecturaPrivadaNoSeGuarda() throws Exception {
        assertThat(cacheControl(consultarPlan().andExpect(status().isOk()))).contains("no-store");
    }

    // Scenario: Error de validación
    @Test
    void unErrorDeValidacionNoSeGuarda() throws Exception {
        ResultActions respuesta = pedir(get("/api/v1/pacientes/listado").param("size", "0"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDACION"));

        assertThat(cacheControl(respuesta)).contains("no-store");
    }

    // Scenario: Recurso inexistente
    @Test
    void unRecursoInexistenteNoSeGuarda() throws Exception {
        ResultActions respuesta = pedir(get("/api/v1/pacientes/" + UUID.randomUUID()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));

        assertThat(cacheControl(respuesta)).contains("no-store");
    }

    // Scenario: Escritura rechazada por plan vencido
    @Test
    void elRechazoPorPlanVencidoNoSeGuarda() throws Exception {
        plan(agenda, "VENCIDO", null);

        ResultActions respuesta = pedir(post("/api/v1/bloqueos").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("PLAN_VENCIDO"));

        assertThat(cacheControl(respuesta)).contains("no-store");
    }

    private static String cacheControl(ResultActions respuesta) {
        MvcResult resultado = respuesta.andReturn();
        String valor = resultado.getResponse().getHeader(HttpHeaders.CACHE_CONTROL);
        return valor == null ? "" : valor;
    }
}
