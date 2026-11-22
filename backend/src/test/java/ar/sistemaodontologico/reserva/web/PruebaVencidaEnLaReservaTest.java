package ar.sistemaodontologico.reserva.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pruebas.agenda.AgendaDePrueba;

/**
 * La reserva pública con la prueba vencida que la tarea diaria todavía no pasó a {@code VENCIDO} (C-20, tarea 4.1;
 * design D6; deltas de reserva-publica y gestion-turno-paciente; RN-RP-11). Datos ficticios (regla dura 17).
 */
class PruebaVencidaEnLaReservaTest extends ApiPublicaTest {

    @Test
    void laPaginaPublicaNoTomaTurnos() throws Exception {
        pruebaVencidaAyer();

        mvc.perform(get(RUTA_CONSULTORIOS + slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tomaTurnosOnline").value(false))
                .andExpect(jsonPath("$.telefono").value(TELEFONO_CONSULTORIO))
                .andExpect(jsonPath("$.prestaciones").isEmpty());
    }

    @Test
    void elUltimoDiaDePruebaSigueTomandoTurnos() throws Exception {
        jdbc.update("UPDATE consultorio SET prueba_hasta = ? WHERE id = ?", hoy(), agenda.consultorio());

        mvc.perform(get(RUTA_CONSULTORIOS + slug)).andExpect(jsonPath("$.tomaTurnosOnline").value(true));
    }

    @Test
    void noSePuedeReservar() throws Exception {
        pruebaVencidaAyer();

        reservar(slug, pedido(lunesA("10:00"), AgendaDePrueba.dniNuevo()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("RESERVA_NO_DISPONIBLE"));
    }

    @Test
    void elLinkPermiteCancelarPeroNoReprogramar() throws Exception {
        String token = reservarYObtenerToken(lunesA("10:00"));
        pruebaVencidaAyer();

        ver(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.puedeModificar").value(true))
                .andExpect(jsonPath("$.puedeReprogramar").value(false));
        reprogramar(token, lunesA("11:00"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("RESERVA_NO_DISPONIBLE"));
        assertThat(jdbc.queryForObject("SELECT inicio FROM turno WHERE id = ?", java.sql.Timestamp.class,
                turnoDe(token)).toInstant()).isEqualTo(lunesA("10:00"));
    }

    @Test
    void cancelarPorLinkYElOdontologoVeLaNovedad() throws Exception {
        String token = reservarYObtenerToken(lunesA("10:00"));
        pruebaVencidaAyer();

        cancelar(token).andExpect(status().isOk()).andExpect(jsonPath("$.turno.estado").value("CANCELADO"));

        UUID turno = turnoDe(token);
        assertThat(jdbc.queryForObject("SELECT novedad_paciente_at IS NOT NULL FROM turno WHERE id = ?",
                Boolean.class, turno)).isTrue();
    }

    @Test
    void conElPlanVencidoElLinkTampocoReprograma() throws Exception {
        String token = reservarYObtenerToken(lunesA("10:00"));
        vencerPlan();

        ver(token)
                .andExpect(jsonPath("$.puedeModificar").value(true))
                .andExpect(jsonPath("$.puedeReprogramar").value(false));
    }

    private LocalDate hoy() {
        return reloj.instant().atZone(ZONA).toLocalDate();
    }

    private void pruebaVencidaAyer() {
        jdbc.update("UPDATE consultorio SET plan = 'PRUEBA', prueba_hasta = ? WHERE id = ?", hoy().minusDays(1),
                agenda.consultorio());
    }
}
