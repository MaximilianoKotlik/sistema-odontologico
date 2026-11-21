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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import pruebas.errores.ControladorDeErroresDePrueba;

/**
 * Spec errores-api (C-02, tarea 4.1). Prueba solo el mapeo de errores: los filtros de seguridad de C-04 se
 * desactivan, porque el 401 de la seguridad tiene su propio test (SeguridadHttpTest).
 */
@WebMvcTest(controllers = ControladorDeErroresDePrueba.class)
@AutoConfigureMockMvc(addFilters = false)
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
    void errorDeNegocioConPropiedadesExtra() throws Exception {
        // C-08 (design D6): FRANJAS_SOLAPADAS lleva los días y SLUG_EN_USO las sugerencias.
        mvc.perform(get("/api/v1/pruebas/con-propiedades"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("FRANJAS_SOLAPADAS"))
                .andExpect(jsonPath("$.dias[0]").value(1))
                .andExpect(jsonPath("$.dias[1]").value(3));
    }

    @Test
    void carrerasDeConfiguracionFrenadasPorLaBase() throws Exception {
        // C-08 (tareas 4.2 y 5.3): la exclusión de franjas y el índice único de prestaciones.
        mvc.perform(get("/api/v1/pruebas/franjas-solapadas-en-la-base"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("FRANJAS_SOLAPADAS"));
        mvc.perform(get("/api/v1/pruebas/prestacion-duplicada-en-la-base"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PRESTACION_DUPLICADA"));
    }

    @Test
    void turnoSolapadoFrenadoPorLaBase() throws Exception {
        // C-11 (design D5): la exclusión de la base es la red de seguridad; sin turnosEnConflicto.
        mvc.perform(get("/api/v1/pruebas/turno-solapado-en-la-base"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("TURNO_SOLAPADO"))
                .andExpect(jsonPath("$.turnosEnConflicto").doesNotExist());
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
