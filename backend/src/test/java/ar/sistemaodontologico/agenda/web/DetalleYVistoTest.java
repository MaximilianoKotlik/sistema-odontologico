package ar.sistemaodontologico.agenda.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pruebas.agenda.AgendaDePrueba.lunesA;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import pruebas.agenda.AgendaDePrueba;

/**
 * {@code GET /api/v1/turnos/{id}} y {@code POST /api/v1/turnos/{id}/visto} (C-12, tareas 2.3 y 2.4; spec
 * agenda-del-odontologo, design D4 y D5, DD-39).
 */
class DetalleYVistoTest extends ApiDeAgendaTest {

    private static final Instant DIEZ = Instant.parse("2026-11-21T13:00:00.123456Z");
    private static final Instant DIEZ_Y_UNO = Instant.parse("2026-11-21T13:01:00.654321Z");

    // --- Detalle (2.3) ---

    @Test
    void detalleConLosDatosDelPacienteLaNotaYElMotivo() throws Exception {
        UUID paciente = agenda.paciente("Ana", "Prueba", "90000001", "+5491100000001", "ana@example.com");
        UUID turno = agenda.turno(paciente, agenda.prestacion("Consulta", 30, true), lunesA("10:00"), 30,
                "CANCELADO", false);
        jdbc.update("UPDATE turno SET motivo_cancelacion = 'Viaja', nota_interna = 'Trae estudios' WHERE id = ?",
                turno);

        pedirDetalle(turno)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(turno.toString()))
                .andExpect(jsonPath("$.inicio").value("2026-11-23T13:00:00Z"))
                .andExpect(jsonPath("$.estado").value("CANCELADO"))
                .andExpect(jsonPath("$.prestacion.nombre").value("Consulta"))
                .andExpect(jsonPath("$.paciente.id").value(paciente.toString()))
                .andExpect(jsonPath("$.paciente.nombre").value("Ana"))
                .andExpect(jsonPath("$.paciente.apellido").value("Prueba"))
                .andExpect(jsonPath("$.paciente.dni").value("90000001"))
                .andExpect(jsonPath("$.paciente.telefono").value("+5491100000001"))
                .andExpect(jsonPath("$.paciente.email").value("ana@example.com"))
                .andExpect(jsonPath("$.paciente.requiereRevision").value(false))
                .andExpect(jsonPath("$.tieneNota").value(true))
                .andExpect(jsonPath("$.notaInterna").value("Trae estudios"))
                .andExpect(jsonPath("$.motivoCancelacion").value("Viaja"))
                .andExpect(jsonPath("$.horarioAnterior").isEmpty())
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void turnoReprogramadoPorElPaciente() throws Exception {
        UUID turno = turnoDeLas10();
        jdbc.update("UPDATE turno SET inicio = ?, fin = ? WHERE id = ?", Timestamp.from(lunesA("16:00")),
                Timestamp.from(lunesA("16:30")), turno);
        agenda.eventoDelPaciente(turno, "TURNO_REPROGRAMADO",
                "{\"inicio\":\"2026-11-23T13:00:00Z\",\"fin\":\"2026-11-23T13:30:00Z\"}",
                "{\"inicio\":\"2026-11-23T19:00:00Z\",\"fin\":\"2026-11-23T19:30:00Z\"}", DIEZ);
        agenda.novedad(turno, DIEZ);

        pedirDetalle(turno)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inicio").value("2026-11-23T19:00:00Z"))
                .andExpect(jsonPath("$.cambioPaciente").value("REPROGRAMO"))
                .andExpect(jsonPath("$.novedadPacienteAt").value("2026-11-21T13:00:00.123456Z"))
                .andExpect(jsonPath("$.horarioAnterior.inicio").value("2026-11-23T13:00:00Z"))
                .andExpect(jsonPath("$.horarioAnterior.fin").value("2026-11-23T13:30:00Z"));
    }

    @Test
    void turnoDeOtroConsultorio() throws Exception {
        UUID deB = turnoDe(otraAgenda());

        pedirDetalle(deB)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
        pedirDetalle(UUID.randomUUID()).andExpect(status().isNotFound());
    }

    // --- Visto (2.4) ---

    @Test
    void marcarComoVisto() throws Exception {
        UUID turno = turnoDeLas10();
        agenda.novedad(turno, DIEZ);

        visto(turno, Map.of("novedadAt", DIEZ.toString())).andExpect(status().isNoContent());

        assertThat(filaDeTurno(turno).get("novedad_paciente_at")).isNull();
        assertThat(accionesAuditadas(turno)).isEmpty();
    }

    @Test
    void novedadNuevaMientrasSeMarcabaComoVisto() throws Exception {
        UUID turno = turnoDeLas10();
        agenda.novedad(turno, DIEZ_Y_UNO);

        visto(turno, Map.of("novedadAt", DIEZ.toString())).andExpect(status().isNoContent());

        assertThat(((Timestamp) filaDeTurno(turno).get("novedad_paciente_at")).toInstant()).isEqualTo(DIEZ_Y_UNO);
    }

    @Test
    void vistoConLaNovedadTruncadaAMilisegundos() throws Exception {
        UUID turno = turnoDeLas10();
        agenda.novedad(turno, DIEZ);

        // Lo que manda un cliente que pasó el valor por Date (nota de Maxi en #39).
        visto(turno, Map.of("novedadAt", "2026-11-21T13:00:00.123Z")).andExpect(status().isNoContent());

        assertThat(filaDeTurno(turno).get("novedad_paciente_at")).isNull();
    }

    @Test
    void vistoSinIndicarLaNovedad() throws Exception {
        UUID turno = turnoDeLas10();
        agenda.novedad(turno, DIEZ);

        visto(turno, Map.of())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
        assertThat(filaDeTurno(turno).get("novedad_paciente_at")).isNotNull();
    }

    @Test
    void vistoDosVeces() throws Exception {
        UUID turno = turnoDeLas10();
        agenda.novedad(turno, DIEZ);

        visto(turno, Map.of("novedadAt", DIEZ.toString())).andExpect(status().isNoContent());
        visto(turno, Map.of("novedadAt", DIEZ.toString())).andExpect(status().isNoContent());
    }

    @Test
    void vistoDeUnTurnoAjeno() throws Exception {
        AgendaDePrueba deB = otraAgenda();
        UUID turno = turnoDe(deB);
        deB.novedad(turno, DIEZ);

        visto(turno, Map.of("novedadAt", DIEZ.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
        assertThat(filaDeTurno(turno).get("novedad_paciente_at")).isNotNull();
    }

    @Test
    void vistoNoCambiaLaVersion() throws Exception {
        UUID turno = turnoDeLas10();
        agenda.novedad(turno, DIEZ);

        visto(turno, Map.of("novedadAt", DIEZ.toString())).andExpect(status().isNoContent());

        assertThat(filaDeTurno(turno).get("version")).isEqualTo(0L);
    }

    private UUID turnoDeLas10() {
        return turnoDe(agenda);
    }

    private static UUID turnoDe(AgendaDePrueba de) {
        return de.turno(de.paciente("Ana", "Prueba"), de.prestacion("Consulta", 30, true), lunesA("10:00"), 30,
                "RESERVADO", false);
    }

    private ResultActions pedirDetalle(UUID turno) throws Exception {
        return mvc.perform(conToken(get("/api/v1/turnos/" + turno), token));
    }

    private ResultActions visto(UUID turno, Map<String, Object> cuerpo) throws Exception {
        return mvc.perform(conCuerpo(post("/api/v1/turnos/" + turno + "/visto"), token, cuerpo));
    }
}
