package ar.sistemaodontologico.plan.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.sistemaodontologico.compartido.web.ManejadorGlobalDeErrores;
import ar.sistemaodontologico.plan.application.ConsultarPlanesPublicos;
import ar.sistemaodontologico.plan.application.FuenteDePlanes;
import ar.sistemaodontologico.plan.domain.CatalogoCrudo;
import ar.sistemaodontologico.plan.domain.CatalogoCrudo.PlanCrudo;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Spec public-plans — contrato de {@code GET /api/v1/public/planes} (design D3) y Problem Details ante una fuente
 * inválida. Sin base de datos: el endpoint no toca tenants ni tablas. Tareas 2.2 y 2.3. La parte de seguridad
 * (sin token) y el OpenAPI están en {@code PlanesPublicosHttpTest}.
 */
class ControladorDePlanesPublicosTest {

    private static final String RUTA = "/api/v1/public/planes";

    private static MockMvc mvc(FuenteDePlanes fuente) {
        return MockMvcBuilders.standaloneSetup(new ControladorDePlanesPublicos(new ConsultarPlanesPublicos(fuente)))
                .setControllerAdvice(new ManejadorGlobalDeErrores())
                .build();
    }

    @Test
    void respondeElCatalogoConImportesComoTextoEnArsYFechaUtc() throws Exception {
        CatalogoCrudo crudo = new CatalogoCrudo(List.of(
                new PlanCrudo("consultorio", "Plan consultorio", "Un odontólogo · un consultorio.",
                        List.of("Agenda diaria y semanal", "Link público"), "14900.00", "ARS", true),
                new PlanCrudo("sin-precio", "Plan futuro", null, List.of("Algo"), "PENDIENTE", "ARS", true)),
                30, false, "2026-11-20T03:00:00Z");

        mvc(() -> crudo).perform(get(RUTA))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.planes.length()").value(1))
                .andExpect(jsonPath("$.planes[0].id").value("consultorio"))
                .andExpect(jsonPath("$.planes[0].nombre").value("Plan consultorio"))
                .andExpect(jsonPath("$.planes[0].descripcion").value("Un odontólogo · un consultorio."))
                .andExpect(jsonPath("$.planes[0].incluye[1]").value("Link público"))
                .andExpect(jsonPath("$.planes[0].importeARS").value(Matchers.isA(String.class)))
                .andExpect(jsonPath("$.planes[0].importeARS").value("14900.00"))
                .andExpect(jsonPath("$.planes[0].moneda").value("ARS"))
                .andExpect(jsonPath("$.planes[0].impuestosIncluidos").value(true))
                .andExpect(jsonPath("$.prueba.dias").value(30))
                .andExpect(jsonPath("$.prueba.requiereMedioDePago").value(false))
                .andExpect(jsonPath("$.actualizadoEn").value("2026-11-20T03:00:00Z"));
    }

    @Test
    void unPlanSinDescripcionLaOmite() throws Exception {
        CatalogoCrudo crudo = new CatalogoCrudo(List.of(
                new PlanCrudo("consultorio", "Plan consultorio", null, List.of("Agenda"), "14900.00", "ARS", true)),
                30, false, "2026-11-20T03:00:00Z");

        mvc(() -> crudo).perform(get(RUTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planes[0].descripcion").doesNotExist());
    }

    @Test
    void conLaFuenteInvalidaRespondeProblemDetailsSinImportes() throws Exception {
        CatalogoCrudo sinPlanes = new CatalogoCrudo(List.of(), 30, false, "2026-11-20T03:00:00Z");

        mvc(() -> sinPlanes).perform(get(RUTA))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value("PLANES_NO_DISPONIBLES"))
                .andExpect(jsonPath("$.planes").doesNotExist())
                .andExpect(content().string(Matchers.not(Matchers.containsString("importe"))));
    }
}
