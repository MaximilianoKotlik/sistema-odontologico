package ar.sistemaodontologico.agenda.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pruebas.agenda.AgendaDePrueba.lunesA;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pruebas.agenda.AgendaDePrueba;

/** {@code GET /api/v1/agenda} (C-12, tareas 2.1 y 2.2; spec agenda-del-odontologo, design D2 y D3). */
class AgendaDelRangoTest extends ApiDeAgendaTest {

    private static final Instant SEMANA_DESDE = AgendaDePrueba.LUNES;
    private static final Instant SEMANA_HASTA = AgendaDePrueba.LUNES.plus(Duration.ofDays(7));

    @Test
    void semanaConTurnosUnSobreturnoYUnBloqueo() throws Exception {
        UUID consulta = agenda.prestacion("Consulta", 30, true);
        UUID paciente = agenda.paciente("Ana", "Prueba");
        UUID confirmado = agenda.turno(paciente, consulta, lunesA("10:00"), 30, "CONFIRMADO", false);
        UUID sobreturno = agenda.turno(paciente, consulta, lunesA("10:00").plus(Duration.ofDays(1)), 30,
                "RESERVADO", true);
        UUID cancelado = agenda.turno(paciente, consulta, lunesA("09:00"), 30, "CANCELADO", false);
        UUID bloqueo = agenda.bloqueo(lunesA("12:00"), lunesA("13:00"), "Trámite");

        pedirAgenda(SEMANA_DESDE, SEMANA_HASTA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.turnos[*].id",
                        contains(cancelado.toString(), confirmado.toString(), sobreturno.toString())))
                .andExpect(jsonPath("$.turnos[0].estado").value("CANCELADO"))
                .andExpect(jsonPath("$.turnos[0].canceladoPor").value("ODONTOLOGO"))
                .andExpect(jsonPath("$.turnos[1].esSobreturno").value(false))
                .andExpect(jsonPath("$.turnos[2].esSobreturno").value(true))
                .andExpect(jsonPath("$.bloqueos[*].id", contains(bloqueo.toString())))
                .andExpect(jsonPath("$.bloqueos[0].motivo").value("Trámite"));
    }

    @Test
    void cadaTurnoTraeLoQueNecesitaLaAgenda() throws Exception {
        UUID consulta = agenda.prestacion("Consulta", 30, true);
        UUID paciente = agenda.paciente("Ana", "Prueba");
        UUID turno = agenda.turno(paciente, consulta, lunesA("10:00"), 30, "RESERVADO", false);
        agenda.nota(turno, "Trae estudios");

        JsonNode respuesta = cuerpo(pedirAgenda(SEMANA_DESDE, SEMANA_HASTA).andExpect(status().isOk()))
                .get("turnos").get(0);

        assertThat(respuesta.get("inicio").asText()).isEqualTo("2026-11-23T13:00:00Z");
        assertThat(respuesta.get("fin").asText()).isEqualTo("2026-11-23T13:30:00Z");
        assertThat(respuesta.get("duracionMin").asInt()).isEqualTo(30);
        assertThat(respuesta.get("origen").asText()).isEqualTo("MANUAL");
        assertThat(respuesta.get("prestacion").get("id").asText()).isEqualTo(consulta.toString());
        assertThat(respuesta.get("prestacion").get("nombre").asText()).isEqualTo("Consulta");
        assertThat(respuesta.get("paciente").get("id").asText()).isEqualTo(paciente.toString());
        assertThat(respuesta.get("paciente").get("nombre").asText()).isEqualTo("Ana");
        assertThat(respuesta.get("paciente").get("apellido").asText()).isEqualTo("Prueba");
        assertThat(respuesta.get("tieneNota").asBoolean()).isTrue();
        assertThat(respuesta.get("canceladoPor").isNull()).isTrue();
        assertThat(respuesta.get("cambioPaciente").isNull()).isTrue();
        assertThat(respuesta.get("novedadPacienteAt").isNull()).isTrue();
        assertThat(respuesta.get("version").asLong()).isZero();
    }

    @Test
    void sinDatosPersonalesDeMas() throws Exception {
        UUID consulta = agenda.prestacion("Consulta", 30, true);
        UUID paciente = agenda.paciente("Ana", "Prueba", "90000001", "+5491100000001", "ana@example.com");
        UUID turno = agenda.turno(paciente, consulta, lunesA("10:00"), 30, "RESERVADO", false);
        agenda.nota(turno, "Texto privado de la nota");

        String respuesta = pedirAgenda(SEMANA_DESDE, SEMANA_HASTA).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(respuesta).doesNotContain("90000001", "+5491100000001", "ana@example.com",
                "Texto privado de la nota", "\"dni\"", "\"telefono\"", "\"email\"", "\"notaInterna\"");
    }

    @Test
    void incluyeLoQueEmpezoAntesDelRangoYTerminaAdentro() throws Exception {
        UUID consulta = agenda.prestacion("Consulta", 60, true);
        UUID paciente = agenda.paciente("Ana", "Prueba");
        UUID turno = agenda.turno(paciente, consulta, lunesA("10:00"), 60, "RESERVADO", false);
        agenda.turno(paciente, consulta, lunesA("12:00"), 60, "RESERVADO", false);

        pedirAgenda(lunesA("10:30"), lunesA("11:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.turnos[*].id", contains(turno.toString())));
    }

    @Test
    void rangoInvalido() throws Exception {
        pedirAgenda(SEMANA_DESDE, SEMANA_DESDE.plus(Duration.ofDays(60)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
        pedirAgenda(SEMANA_HASTA, SEMANA_DESDE)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
        pedirAgenda(SEMANA_DESDE, SEMANA_DESDE.plus(Duration.ofDays(42))).andExpect(status().isOk());
    }

    @Test
    void agendaDeOtroConsultorio() throws Exception {
        UUID consulta = agenda.prestacion("Consulta", 30, true);
        UUID propio = agenda.turno(agenda.paciente("Ana", "Prueba"), consulta, lunesA("10:00"), 30, "RESERVADO",
                false);
        AgendaDePrueba deB = otraAgenda();
        deB.turno(deB.paciente("Carla", "Ficticia"), deB.prestacion("Consulta", 30, true), lunesA("11:00"), 30,
                "RESERVADO", false);
        deB.bloqueo(lunesA("12:00"), lunesA("13:00"), null);

        pedirAgenda(SEMANA_DESDE, SEMANA_HASTA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.turnos[*].id", contains(propio.toString())))
                .andExpect(jsonPath("$.bloqueos", hasSize(0)));
    }

    // --- cambioPaciente desde la auditoría (2.2, D3) ---

    @Test
    void reservaPorLinkSinVer() throws Exception {
        UUID turno = turnoDeLas10();
        Instant novedad = Instant.parse("2026-11-21T14:00:00.123456Z");
        agenda.eventoDelPaciente(turno, "TURNO_CREADO", null, "{\"estado\":\"RESERVADO\"}", novedad);
        agenda.novedad(turno, novedad);

        pedirAgenda(SEMANA_DESDE, SEMANA_HASTA)
                .andExpect(jsonPath("$.turnos[0].cambioPaciente").value("RESERVO"))
                .andExpect(jsonPath("$.turnos[0].novedadPacienteAt").value("2026-11-21T14:00:00.123456Z"));
    }

    @Test
    void elTipoEsElDelUltimoCambioDelPaciente() throws Exception {
        UUID reprogramado = turnoDeLas10();
        agenda.eventoDelPaciente(reprogramado, "TURNO_CREADO", null, "{\"estado\":\"RESERVADO\"}",
                Instant.parse("2026-11-20T10:00:00Z"));
        agenda.eventoDelPaciente(reprogramado, "TURNO_REPROGRAMADO",
                "{\"inicio\":\"2026-11-23T12:00:00Z\",\"fin\":\"2026-11-23T12:30:00Z\"}",
                "{\"inicio\":\"2026-11-23T13:00:00Z\",\"fin\":\"2026-11-23T13:30:00Z\"}",
                Instant.parse("2026-11-21T10:00:00Z"));
        agenda.novedad(reprogramado, Instant.parse("2026-11-21T10:00:00Z"));

        UUID cancelado = agenda.turno(agenda.paciente("Carla", "Ficticia"), agenda.prestacion("Control", 30, true),
                lunesA("11:00"), 30, "CANCELADO", false);
        jdbc.update("UPDATE turno SET cancelado_por = 'PACIENTE' WHERE id = ?", cancelado);
        agenda.eventoDelPaciente(cancelado, "TURNO_ESTADO_CAMBIADO",
                "{\"estado\":\"RESERVADO\",\"cancelado_por\":null}",
                "{\"estado\":\"CANCELADO\",\"cancelado_por\":\"PACIENTE\"}", Instant.parse("2026-11-21T11:00:00Z"));
        agenda.novedad(cancelado, Instant.parse("2026-11-21T11:00:00Z"));

        pedirAgenda(SEMANA_DESDE, SEMANA_HASTA)
                .andExpect(jsonPath("$.turnos[0].cambioPaciente").value("REPROGRAMO"))
                .andExpect(jsonPath("$.turnos[1].cambioPaciente").value("CANCELO"))
                .andExpect(jsonPath("$.turnos[1].canceladoPor").value("PACIENTE"));
    }

    @Test
    void sinNovedadNoHayCambioAunqueElPacienteLoHayaReservado() throws Exception {
        UUID turno = turnoDeLas10();
        agenda.eventoDelPaciente(turno, "TURNO_CREADO", null, "{\"estado\":\"RESERVADO\"}",
                Instant.parse("2026-11-20T10:00:00Z"));

        pedirAgenda(SEMANA_DESDE, SEMANA_HASTA).andExpect(jsonPath("$.turnos[0].cambioPaciente").isEmpty());
    }

    @Test
    void losEventosDelOdontologoNoCuentan() throws Exception {
        UUID turno = turnoDeLas10();
        agenda.eventoDelPaciente(turno, "TURNO_CREADO", null, "{\"estado\":\"RESERVADO\"}",
                Instant.parse("2026-11-20T10:00:00Z"));
        jdbc.update("""
                INSERT INTO evento_auditoria (id, consultorio_id, ocurrido_at, actor_tipo, actor_id, entidad, entidad_id,
                                              accion, valor_anterior, valor_nuevo)
                VALUES (?, ?, '2026-11-21T10:00:00Z', 'ODONTOLOGO', ?, 'turno', ?, 'TURNO_REPROGRAMADO', '{}', '{}')""",
                UUID.randomUUID(), agenda.consultorio(), agenda.usuario(), turno);
        agenda.novedad(turno, Instant.parse("2026-11-20T10:00:00Z"));

        pedirAgenda(SEMANA_DESDE, SEMANA_HASTA).andExpect(jsonPath("$.turnos[0].cambioPaciente").value("RESERVO"));
    }

    private UUID turnoDeLas10() {
        return agenda.turno(agenda.paciente("Ana", "Prueba"), agenda.prestacion("Consulta", 30, true),
                lunesA("10:00"), 30, "RESERVADO", false);
    }

    private org.springframework.test.web.servlet.ResultActions pedirAgenda(Instant desde, Instant hasta)
            throws Exception {
        return pedirGet("/api/v1/agenda?desde=" + desde + "&hasta=" + hasta);
    }
}
