package ar.sistemaodontologico.agenda.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pruebas.agenda.AgendaDePrueba;

/**
 * {@code GET /api/v1/turnos/fuera-de-disponibilidad} (C-12, tarea 2.5; spec agenda-del-odontologo, design D9;
 * RN-AG-03). La grilla de prueba atiende de lunes a viernes de 09:00 a 13:00. Las fechas salen del reloj real para que
 * el "futuro" y el "pasado" no dependan de la semana en que corre el test.
 */
class FueraDeDisponibilidadTest extends ApiDeAgendaTest {

    private static final ZoneId BUENOS_AIRES = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final String RUTA = "/api/v1/turnos/fuera-de-disponibilidad";

    private UUID prestacion;
    private UUID paciente;

    @BeforeEach
    void cargarDatos() {
        prestacion = agenda.prestacion("Consulta", 30, true);
        paciente = agenda.paciente("Ana", "Prueba");
    }

    @Test
    void grillaAchicada() throws Exception {
        UUID afuera = agenda.turno(paciente, prestacion, proximoMartesA("18:00"), 30, "CONFIRMADO", false);
        agenda.turno(paciente, prestacion, proximoMartesA("10:00"), 30, "CONFIRMADO", false);

        pedirGet(RUTA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(afuera.toString())))
                .andExpect(jsonPath("$[0].estado").value("CONFIRMADO"))
                .andExpect(jsonPath("$[0].paciente.nombre").value("Ana"));
        assertThat(filaDeTurno(afuera).get("estado")).isEqualTo("CONFIRMADO");
    }

    @Test
    void ordenadosPorInicio() throws Exception {
        UUID despues = agenda.turno(paciente, prestacion, proximoMartesA("18:00").plusSeconds(7 * 86_400L), 30,
                "RESERVADO", false);
        UUID antes = agenda.turno(paciente, prestacion, proximoMartesA("08:00"), 30, "RESERVADO", false);

        pedirGet(RUTA).andExpect(jsonPath("$[*].id", contains(antes.toString(), despues.toString())));
    }

    @Test
    void sobreturnosTurnosPasadosYCanceladosNoCuentan() throws Exception {
        agenda.turno(paciente, prestacion, proximoMartesA("18:00"), 30, "RESERVADO", true);
        agenda.turno(paciente, prestacion, proximoMartesA("18:00").minusSeconds(14 * 86_400L), 30, "RESERVADO",
                false);
        agenda.turno(paciente, prestacion, proximoMartesA("19:00"), 30, "CANCELADO", false);

        pedirGet(RUTA).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void soloLosDelConsultorio() throws Exception {
        AgendaDePrueba deB = otraAgenda();
        deB.turno(deB.paciente("Carla", "Ficticia"), deB.prestacion("Consulta", 30, true), proximoMartesA("18:00"),
                30, "RESERVADO", false);

        pedirGet(RUTA).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    /** El martes que viene en Buenos Aires, al menos a dos días, a esa hora local. */
    private static Instant proximoMartesA(String hora) {
        LocalDate hoy = LocalDate.ofInstant(Clock.systemUTC().instant(), BUENOS_AIRES);
        LocalDate martes = hoy.plusDays(2).with(TemporalAdjusters.nextOrSame(DayOfWeek.TUESDAY));
        return martes.atTime(LocalTime.parse(hora)).atZone(BUENOS_AIRES).toInstant();
    }
}
