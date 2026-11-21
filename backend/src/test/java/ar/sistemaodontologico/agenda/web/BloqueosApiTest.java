package ar.sistemaodontologico.agenda.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pruebas.agenda.AgendaDePrueba.lunesA;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pruebas.agenda.AgendaDePrueba;

/** {@code /api/v1/bloqueos} (C-12, tarea 3.5; spec api-de-turnos-y-bloqueos, design D8; US-012, RN-AG-05). */
class BloqueosApiTest extends ApiDeAgendaTest {

    private static final String RUTA = "/api/v1/bloqueos";

    @Test
    void crearUnBloqueo() throws Exception {
        String respuesta = pedirPost(RUTA, Map.of("inicio", lunesA("12:00"), "fin", lunesA("13:00"), "motivo",
                "Trámite"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.inicio").value("2026-11-23T15:00:00Z"))
                .andExpect(jsonPath("$.fin").value("2026-11-23T16:00:00Z"))
                .andExpect(jsonPath("$.motivo").value("Trámite"))
                .andReturn().getResponse().getContentAsString();

        UUID id = UUID.fromString(json.readTree(respuesta).get("id").asText());
        assertThat(accionesAuditadas(id)).containsExactly("BLOQUEO_CREADO");
    }

    @Test
    void bloqueoSinMotivo() throws Exception {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("inicio", lunesA("12:00"));
        cuerpo.put("fin", lunesA("13:00"));

        pedirPost(RUTA, cuerpo).andExpect(status().isCreated()).andExpect(jsonPath("$.motivo").isEmpty());
    }

    @Test
    void bloqueoSobreTurnos() throws Exception {
        agenda.franja(4, "14:00", "19:00");
        UUID consulta = agenda.prestacion("Consulta", 30, true);
        UUID paciente = agenda.paciente("Ana", "Prueba");
        Instant jueves = lunesA("15:00").plus(Duration.ofDays(3));
        UUID primero = agenda.turno(paciente, consulta, jueves.plus(Duration.ofMinutes(30)), 30, "RESERVADO", false);
        UUID segundo = agenda.turno(paciente, consulta, jueves.plus(Duration.ofHours(2)), 30, "CONFIRMADO", false);

        pedirPost(RUTA, Map.of("inicio", jueves, "fin", jueves.plus(Duration.ofHours(4))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("BLOQUEO_CON_TURNOS"))
                .andExpect(jsonPath("$.turnosEnConflicto", containsInAnyOrder(primero.toString(), segundo.toString())));
    }

    @Test
    void rangoInvalido() throws Exception {
        pedirPost(RUTA, Map.of("inicio", lunesA("13:00"), "fin", lunesA("12:00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("HORARIO_INVALIDO"));
        pedirPost(RUTA, Map.of("inicio", lunesA("12:00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    @Test
    void eliminarUnBloqueo() throws Exception {
        UUID bloqueo = agenda.bloqueo(lunesA("12:00"), lunesA("13:00"), null);

        pedirDelete(RUTA + "/" + bloqueo).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM bloqueo WHERE id = ?", Long.class, bloqueo)).isZero();
        assertThat(accionesAuditadas(bloqueo)).containsExactly("BLOQUEO_ELIMINADO");
    }

    @Test
    void eliminarUnBloqueoAjeno() throws Exception {
        AgendaDePrueba deB = otraAgenda();
        UUID bloqueo = deB.bloqueo(lunesA("12:00"), lunesA("13:00"), null);

        pedirDelete(RUTA + "/" + bloqueo)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bloqueo WHERE id = ?", Long.class, bloqueo)).isOne();
    }

    @Test
    void bloqueosFuturos() throws Exception {
        Instant ahora = enLaGrilla(Clock.systemUTC().instant());
        agenda.bloqueo(ahora.minus(Duration.ofDays(2)), ahora.minus(Duration.ofDays(1)), "Terminó ayer");
        UUID segundo = agenda.bloqueo(ahora.plus(Duration.ofDays(8)), ahora.plus(Duration.ofDays(9)), null);
        UUID primero = agenda.bloqueo(ahora.plus(Duration.ofDays(7)), ahora.plus(Duration.ofDays(7).plusHours(2)),
                "Congreso");

        pedirGet(RUTA + "?desde=" + ahora)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(primero.toString(), segundo.toString())))
                .andExpect(jsonPath("$[0].motivo").value("Congreso"));
        pedirGet(RUTA).andExpect(jsonPath("$[*].id", contains(primero.toString(), segundo.toString())));
    }

    @Test
    void unBloqueoEnCursoSigueVigente() throws Exception {
        Instant ahora = enLaGrilla(Clock.systemUTC().instant());
        UUID enCurso = agenda.bloqueo(ahora.minus(Duration.ofHours(1)), ahora.plus(Duration.ofHours(1)), null);

        pedirGet(RUTA + "?desde=" + ahora).andExpect(jsonPath("$[*].id", contains(enCurso.toString())));
    }

    @Test
    void soloLosBloqueosDelConsultorio() throws Exception {
        Instant ahora = enLaGrilla(Clock.systemUTC().instant());
        AgendaDePrueba deB = otraAgenda();
        deB.bloqueo(ahora.plus(Duration.ofDays(7)), ahora.plus(Duration.ofDays(8)), null);

        pedirGet(RUTA + "?desde=" + ahora).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    private static Instant enLaGrilla(Instant instante) {
        return Instant.ofEpochSecond(Math.floorDiv(instante.getEpochSecond(), 300) * 300);
    }
}
