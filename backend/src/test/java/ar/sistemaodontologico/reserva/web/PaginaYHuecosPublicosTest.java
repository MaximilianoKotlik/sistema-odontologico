package ar.sistemaodontologico.reserva.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import pruebas.agenda.AgendaDePrueba;

/** Página pública y huecos (C-15, tareas 5.1, 5.2 y 8.4; spec reserva-publica). */
class PaginaYHuecosPublicosTest extends ApiPublicaTest {

    @Test
    void consultorioQueTomaTurnos() throws Exception {
        UUID oculta = agenda.prestacion("Control", 20, true);
        jdbc.update("UPDATE tipo_prestacion SET visible_publica = false WHERE id = ?", oculta);

        mvc.perform(get(RUTA_CONSULTORIOS + slug)).andExpect(status().isOk())
                .andExpect(jsonPath("$.tomaTurnosOnline").value(true))
                .andExpect(jsonPath("$.nombre").value("Consultorio Demo"))
                .andExpect(jsonPath("$.direccion").value("Calle Falsa 123"))
                .andExpect(jsonPath("$.telefono").value(TELEFONO_CONSULTORIO))
                .andExpect(jsonPath("$.zonaHoraria").value(ZONA.getId()))
                .andExpect(jsonPath("$.prestaciones", hasSize(1)))
                .andExpect(jsonPath("$.prestaciones[0].id").value(consulta.toString()))
                .andExpect(jsonPath("$.prestaciones[0].nombre").value("Consulta"))
                .andExpect(jsonPath("$.prestaciones[0].duracionMin").value(30))
                .andExpect(jsonPath("$.plan").doesNotExist())
                .andExpect(jsonPath("$.id").doesNotExist());
    }

    @Test
    void informaElUltimoDiaReservable() throws Exception {
        LocalDate esperado = reloj.instant().plusSeconds(60 * 86_400L).atZone(ZONA).toLocalDate();

        mvc.perform(get(RUTA_CONSULTORIOS + slug)).andExpect(jsonPath("$.reservableHasta").value(esperado.toString()));
    }

    @Test
    void prestacionDesactivadaNoAparece() throws Exception {
        agenda.prestacion("Vieja", 30, false);

        mvc.perform(get(RUTA_CONSULTORIOS + slug)).andExpect(jsonPath("$.prestaciones", hasSize(1)));
    }

    @Test
    void slugInexistente() throws Exception {
        mvc.perform(get(RUTA_CONSULTORIOS + "no-existe-este")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }

    @Test
    void reservaDesactivadaMuestraTelefonoSinPrestaciones() throws Exception {
        desactivarReserva();

        mvc.perform(get(RUTA_CONSULTORIOS + slug)).andExpect(status().isOk())
                .andExpect(jsonPath("$.tomaTurnosOnline").value(false))
                .andExpect(jsonPath("$.telefono").value(TELEFONO_CONSULTORIO))
                .andExpect(jsonPath("$.prestaciones", hasSize(0)));
    }

    @Test
    void planVencidoNoTomaTurnos() throws Exception {
        vencerPlan();

        mvc.perform(get(RUTA_CONSULTORIOS + slug)).andExpect(jsonPath("$.tomaTurnosOnline").value(false));
    }

    @Test
    void huecosDeUnDiaConUnTurno() throws Exception {
        UUID paciente = agenda.paciente("Ana", "Prueba");
        agenda.turno(paciente, consulta, lunesA("09:00"), 30, "RESERVADO", false);

        JsonNode dia = cuerpo(huecos(lunes(), lunes()).andExpect(status().isOk())).path("dias").get(0);

        assertThat(dia.path("fecha").asText()).isEqualTo(lunes().toString());
        assertThat(dia.path("atiende").asBoolean()).isTrue();
        assertThat(huecosDe(dia)).doesNotContain(lunesA("09:00").toString(), lunesA("09:15").toString())
                .contains(lunesA("09:30").toString());
    }

    @Test
    void domingoNoAtiende() throws Exception {
        LocalDate domingo = lunes().plusDays(6);

        JsonNode dia = cuerpo(huecos(domingo, domingo)).path("dias").get(0);

        assertThat(dia.path("atiende").asBoolean()).isFalse();
        assertThat(huecosDe(dia)).isEmpty();
    }

    @Test
    void sobreturnoOcupaElHueco() throws Exception {
        agenda.turno(agenda.paciente("Ana", "Prueba"), consulta, lunesA("10:00"), 30, "RESERVADO", true);

        JsonNode dia = cuerpo(huecos(lunes(), lunes())).path("dias").get(0);

        assertThat(huecosDe(dia)).doesNotContain(lunesA("09:45").toString(), lunesA("10:00").toString(),
                lunesA("10:15").toString());
    }

    @Test
    void respetaAnticipacionYHorizonte() throws Exception {
        jdbc.update("UPDATE consultorio SET horizonte_reserva_dias = 5 WHERE id = ?", agenda.consultorio());

        JsonNode dia = cuerpo(huecos(lunes(), lunes())).path("dias").get(0);

        assertThat(dia.path("atiende").asBoolean()).isTrue();
        assertThat(huecosDe(dia)).isEmpty();
    }

    @Test
    void sinFechasDevuelveCatorceDias() throws Exception {
        mvc.perform(get(RUTA_CONSULTORIOS + slug + "/huecos").param("prestacionId", consulta.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.zonaHoraria").value(ZONA.getId()))
                .andExpect(jsonPath("$.dias", hasSize(14)));
    }

    @Test
    void rangoDemasiadoLargo() throws Exception {
        huecos(lunes(), lunes().plusDays(40)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    @Test
    void rangoInvertido() throws Exception {
        huecos(lunes().plusDays(1), lunes()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    @Test
    void prestacionDeOtroConsultorioEs404() throws Exception {
        UUID ajena = AgendaDePrueba.nueva(jdbc).prestacion("Consulta", 30, true);

        mvc.perform(get(RUTA_CONSULTORIOS + slug + "/huecos").param("prestacionId", ajena.toString()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }

    @Test
    void prestacionNoVisibleEs404() throws Exception {
        jdbc.update("UPDATE tipo_prestacion SET visible_publica = false WHERE id = ?", consulta);

        huecos(lunes(), lunes()).andExpect(status().isNotFound());
    }

    @Test
    void conReservaDesactivadaLosHuecosSon409() throws Exception {
        desactivarReserva();

        huecos(lunes(), lunes()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("RESERVA_NO_DISPONIBLE"))
                .andExpect(jsonPath("$.telefonoContacto").value(TELEFONO_CONSULTORIO));
    }

    @Test
    void losHuecosSonInstantesUtc() throws Exception {
        JsonNode dia = cuerpo(huecos(lunes(), lunes())).path("dias").get(0);

        assertThat(Instant.parse(huecosDe(dia).getFirst())).isEqualTo(lunesA("09:00"));
    }

    private ResultActions huecos(LocalDate desde, LocalDate hasta) throws Exception {
        return mvc.perform(get(RUTA_CONSULTORIOS + slug + "/huecos").param("prestacionId", consulta.toString())
                .param("desde", desde.toString()).param("hasta", hasta.toString()));
    }
}
