package ar.sistemaodontologico.consultorio.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import pruebas.agenda.AgendaDePrueba;

/** {@code GET /api/v1/plan} (C-20, tarea 1.2; design D5; spec plan-del-consultorio §Consulta del plan). */
class ConsultaDelPlanTest extends ApiDelPlanTest {

    @Test
    void cincoDiasDePrueba() throws Exception {
        plan(agenda, "PRUEBA", hoy().plusDays(4));

        consultarPlan()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("PRUEBA"))
                .andExpect(jsonPath("$.pruebaHasta").value(hoy().plusDays(4).toString()))
                .andExpect(jsonPath("$.diasDePruebaRestantes").value(5))
                .andExpect(jsonPath("$.soloLectura").value(false));
    }

    @Test
    void pruebaVencidaAntesDeLaTarea() throws Exception {
        pruebaVencidaAyer(agenda);

        consultarPlan()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("VENCIDO"))
                .andExpect(jsonPath("$.pruebaHasta").value(hoy().minusDays(1).toString()))
                .andExpect(jsonPath("$.diasDePruebaRestantes").isEmpty())
                .andExpect(jsonPath("$.soloLectura").value(true));
    }

    @Test
    void planVencido() throws Exception {
        plan(agenda, "VENCIDO", hoy().minusDays(3));

        consultarPlan()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("VENCIDO"))
                .andExpect(jsonPath("$.soloLectura").value(true));
    }

    @Test
    void planActivo() throws Exception {
        plan(agenda, "ACTIVO", hoy().minusDays(40));

        consultarPlan()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("ACTIVO"))
                .andExpect(jsonPath("$.diasDePruebaRestantes").isEmpty())
                .andExpect(jsonPath("$.soloLectura").value(false));
    }

    @Test
    void soloElPlanDelConsultorioDeLaSesion() throws Exception {
        plan(agenda, "PRUEBA", hoy().plusDays(10));
        AgendaDePrueba otro = AgendaDePrueba.nueva(jdbc);
        plan(otro, "VENCIDO", hoy().minusDays(1));

        consultarPlan()
                .andExpect(jsonPath("$.plan").value("PRUEBA"))
                .andExpect(jsonPath("$.diasDePruebaRestantes").value(11));
    }

    @Test
    void sinSesion() throws Exception {
        mvc.perform(get("/api/v1/plan")).andExpect(status().isUnauthorized());
    }
}
