package ar.sistemaodontologico.compartido.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import pruebas.errores.ControladorDeErroresDePrueba;

/** Spec errores-api (C-02, tarea 4.1). */
@WebMvcTest(controllers = ControladorDeErroresDePrueba.class)
@Import(ControladorDeErroresDePrueba.class)
@ExtendWith(OutputCaptureExtension.class)
class ManejadorGlobalDeErroresTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void errorDeNegocioEnProblemDetailsConCodigoEstable() throws Exception {
        mvc.perform(get("/api/v1/pruebas/conflicto"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value("SLUG_EN_USO"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.type").value("urn:agenda:error:slug-en-uso"))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.instance").value("/api/v1/pruebas/conflicto"));
    }

    @Test
    void validacionConUnErrorPorCampo() throws Exception {
        mvc.perform(post("/api/v1/pruebas/alta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"\", \"email\": \"no-es-un-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores", hasSize(2)))
                .andExpect(jsonPath("$.errores[?(@.campo == 'nombre')].codigo").value("NOT_BLANK"))
                .andExpect(jsonPath("$.errores[?(@.campo == 'email')].codigo").value("EMAIL"));
    }

    @Test
    void bodyIlegible() throws Exception {
        mvc.perform(post("/api/v1/pruebas/alta").contentType(MediaType.APPLICATION_JSON).content("{nombre:"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value("CUERPO_INVALIDO"));
    }

    @Test
    void recursoNoEncontrado() throws Exception {
        mvc.perform(get("/api/v1/pruebas/no-encontrado"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }

    @Test
    void rutaInexistenteTambienTieneCodigo() throws Exception {
        mvc.perform(get("/api/v1/pruebas/no-existe"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }

    @Test
    void metodoNoPermitidoTieneCodigo() throws Exception {
        mvc.perform(post("/api/v1/pruebas/conflicto"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.codigo").value("METODO_NO_PERMITIDO"));
    }

    @Test
    void conflictoDeConcurrencia() throws Exception {
        mvc.perform(get("/api/v1/pruebas/concurrencia"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO_CONCURRENCIA"));
    }

    @Test
    void errorInesperadoSinDetallesNiDatosPersonales(CapturedOutput salida) throws Exception {
        String cuerpo = mvc.perform(get("/api/v1/pruebas/inesperado"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value("ERROR_INTERNO"))
                .andExpect(jsonPath("$.referencia").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        assertThat(cuerpo)
                .doesNotContain("+5491100000099")
                .doesNotContain("paciente@example.com")
                .doesNotContain("falló al procesar")
                .doesNotContain("IllegalStateException")
                .doesNotContain("at ar.");
        // El detalle queda en el log, con los datos personales enmascarados (RN-GL-01).
        assertThat(salida.getAll())
                .contains("IllegalStateException")
                .doesNotContain("+5491100000099")
                .doesNotContain("paciente@example.com");
    }

    @Test
    void operacionSinTenantEsErrorInterno() throws Exception {
        mvc.perform(get("/api/v1/pruebas/sin-tenant"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.codigo").value("ERROR_INTERNO"));
    }
}
