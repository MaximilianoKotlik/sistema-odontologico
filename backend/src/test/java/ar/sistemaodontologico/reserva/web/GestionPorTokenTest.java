package ar.sistemaodontologico.reserva.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import pruebas.agenda.AgendaDePrueba;

/** Gestión del turno por token (C-15, tareas 7.1 a 7.5 y 8.4; spec gestion-turno-paciente). */
class GestionPorTokenTest extends ApiPublicaTest {

    private static final String TOKEN_INEXISTENTE = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    @Test
    void vistaDentroDelPlazo() throws Exception {
        String token = reservarYObtenerToken(lunesA("10:00"));

        ver(token).andExpect(status().isOk())
                .andExpect(jsonPath("$.turno.inicio").value(lunesA("10:00").toString()))
                .andExpect(jsonPath("$.turno.fin").value(lunesA("10:30").toString()))
                .andExpect(jsonPath("$.turno.prestacion").value("Consulta"))
                .andExpect(jsonPath("$.turno.estado").value("RESERVADO"))
                .andExpect(jsonPath("$.paciente.nombre").value("Paciente"))
                .andExpect(jsonPath("$.paciente.apellido").value("Prueba"))
                .andExpect(jsonPath("$.consultorio.nombre").value("Consultorio Demo"))
                .andExpect(jsonPath("$.consultorio.direccion").value("Calle Falsa 123"))
                .andExpect(jsonPath("$.consultorio.telefono").value(TELEFONO_CONSULTORIO))
                .andExpect(jsonPath("$.consultorio.zonaHoraria").value(ZONA.getId()))
                .andExpect(jsonPath("$.puedeModificar").value(true))
                .andExpect(jsonPath("$.puedeReprogramar").value(true))
                .andExpect(jsonPath("$.modificableHasta").value(lunesA("10:00").minusSeconds(86_400).toString()));
    }

    @Test
    void vistaFueraDelPlazo() throws Exception {
        String token = reservarYObtenerToken(lunesA("10:00"));
        plazoVencidoPara(token);

        ver(token).andExpect(status().isOk()).andExpect(jsonPath("$.puedeModificar").value(false))
                .andExpect(jsonPath("$.puedeReprogramar").value(false))
                .andExpect(jsonPath("$.consultorio.telefono").value(TELEFONO_CONSULTORIO));
    }

    @Test
    void laVistaNoTieneDatosInternos() throws Exception {
        String token = reservarYObtenerToken(lunesA("10:00"));
        UUID turno = turnoDe(token);
        jdbc.update("UPDATE turno SET nota_interna = 'NOTA-SECRETA' WHERE id = ?", turno);
        String dni = jdbc.queryForObject("SELECT p.dni FROM paciente p JOIN turno t ON t.paciente_id = p.id WHERE t.id = ?",
                String.class, turno);

        String cuerpo = ver(token).andReturn().getResponse().getContentAsString();

        assertThat(cuerpo).doesNotContain("NOTA-SECRETA", dni, "+5491100000001", agenda.consultorio().toString());
    }

    @Test
    void laVistaNoSeCachea() throws Exception {
        ver(reservarYObtenerToken(lunesA("10:00"))).andExpect(header().string("Cache-Control", Matchers.containsString("no-store")));
        ver(TOKEN_INEXISTENTE).andExpect(header().string("Cache-Control", Matchers.containsString("no-store")));
    }

    @Test
    void elCuatroCeroCuatroEsIdenticoEnTodosLosCasos() throws Exception {
        String terminado = reservarYObtenerToken(lunesA("09:00"));
        jdbc.update("UPDATE turno SET inicio = inicio - interval '60 days', fin = fin - interval '60 days' WHERE id = ?",
                turnoDe(terminado));
        String ausente = reservarYObtenerToken(lunesA("10:00"));
        jdbc.update("UPDATE turno SET estado = 'AUSENTE' WHERE id = ?", turnoDe(ausente));

        String base = ver(TOKEN_INEXISTENTE).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO")).andReturn().getResponse().getContentAsString();

        assertThat(base).doesNotContain(TOKEN_INEXISTENTE);
        for (String token : new String[] {terminado, ausente, "corto", "a+b"}) {
            assertThat(ver(token).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString())
                    .isEqualTo(base);
        }
    }

    @Test
    void canceladoPorElPaciente() throws Exception {
        String token = reservarYObtenerToken(lunesA("10:00"));

        cancelar(token).andExpect(status().isOk()).andExpect(jsonPath("$.turno.estado").value("CANCELADO"));

        Map<String, Object> turno = jdbc.queryForMap("SELECT estado, cancelado_por, novedad_paciente_at FROM turno WHERE id = ?",
                turnoDe(token));
        assertThat(turno).containsEntry("estado", "CANCELADO").containsEntry("cancelado_por", "PACIENTE");
        assertThat(turno.get("novedad_paciente_at")).isNotNull();
        assertThat(jdbc.queryForObject("SELECT revocado_at FROM token_gestion_turno WHERE turno_id = ?", Timestamp.class,
                turnoDe(token))).isNotNull();
        assertThat(acciones()).contains("TURNO_ESTADO_CAMBIADO");
        assertThat(jdbc.queryForObject("""
                SELECT actor_tipo FROM evento_auditoria WHERE entidad_id = ? AND accion = 'TURNO_ESTADO_CAMBIADO'""",
                String.class, turnoDe(token))).isEqualTo("PACIENTE");
        ver(token).andExpect(status().isOk()).andExpect(jsonPath("$.turno.canceladoPor").value("PACIENTE"))
                .andExpect(jsonPath("$.puedeModificar").value(false));
        cancelar(token).andExpect(status().isNotFound());
    }

    @Test
    void cancelarLiberaElHorario() throws Exception {
        cancelar(reservarYObtenerToken(lunesA("10:00"))).andExpect(status().isOk());

        reservarYObtenerToken(lunesA("10:00"));
    }

    @Test
    void canceladoPorElOdontologoSeVeSinAccionesNiMotivo() throws Exception {
        String token = reservarYObtenerToken(lunesA("10:00"));
        jdbc.update("""
                UPDATE turno SET estado = 'CANCELADO', cancelado_por = 'ODONTOLOGO', motivo_cancelacion = 'MOTIVO-INTERNO'
                 WHERE id = ?""", turnoDe(token));

        String cuerpo = ver(token).andExpect(status().isOk()).andExpect(jsonPath("$.turno.estado").value("CANCELADO"))
                .andExpect(jsonPath("$.turno.canceladoPor").value("ODONTOLOGO"))
                .andExpect(jsonPath("$.puedeModificar").value(false)).andExpect(jsonPath("$.puedeReprogramar").value(false))
                .andExpect(jsonPath("$.consultorio.telefono").value(TELEFONO_CONSULTORIO))
                .andReturn().getResponse().getContentAsString();

        assertThat(cuerpo).doesNotContain("MOTIVO-INTERNO");
        reprogramar(token, lunesA("11:00")).andExpect(status().isNotFound());
        cancelar(token).andExpect(status().isNotFound());
        huecos(token).andExpect(status().isNotFound());
    }

    @Test
    void canceladoQueYaTerminoEs404() throws Exception {
        String token = reservarYObtenerToken(lunesA("10:00"));
        jdbc.update("""
                UPDATE turno SET estado = 'CANCELADO', cancelado_por = 'ODONTOLOGO', inicio = inicio - interval '60 days',
                       fin = fin - interval '60 days' WHERE id = ?""", turnoDe(token));

        ver(token).andExpect(status().isNotFound());
    }

    @Test
    void cancelarFueraDePlazo() throws Exception {
        String token = reservarYObtenerToken(lunesA("10:00"));
        plazoVencidoPara(token);

        cancelar(token).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("FUERA_DE_PLAZO"))
                .andExpect(jsonPath("$.telefonoContacto").value(TELEFONO_CONSULTORIO));

        assertThat(jdbc.queryForObject("SELECT estado FROM turno WHERE id = ?", String.class, turnoDe(token)))
                .isEqualTo("RESERVADO");
    }

    @Test
    void cancelarConPlanVencido() throws Exception {
        String token = reservarYObtenerToken(lunesA("10:00"));
        vencerPlan();

        cancelar(token).andExpect(status().isOk()).andExpect(jsonPath("$.turno.estado").value("CANCELADO"));
    }

    @Test
    void huecosParaReprogramarNoCuentanElPropioTurno() throws Exception {
        String token = reservarYObtenerToken(lunesA("10:00"));

        JsonNode dia = cuerpo(huecos(token).andExpect(status().isOk())).path("dias").get(0);

        assertThat(huecosDe(dia)).contains(lunesA("10:00").toString(), lunesA("10:15").toString());
    }

    @Test
    void reprogramacionExitosa() throws Exception {
        String token = reservarYObtenerToken(lunesA("10:00"));
        UUID turno = turnoDe(token);
        jdbc.update("UPDATE turno SET estado = 'CONFIRMADO', version = version + 1, novedad_paciente_at = NULL WHERE id = ?", turno);

        reprogramar(token, lunesA("11:00")).andExpect(status().isOk())
                .andExpect(jsonPath("$.turno.inicio").value(lunesA("11:00").toString()))
                .andExpect(jsonPath("$.turno.estado").value("CONFIRMADO"));

        assertThat(turnoDe(token)).isEqualTo(turno);
        assertThat(jdbc.queryForObject("SELECT novedad_paciente_at FROM turno WHERE id = ?", Timestamp.class, turno))
                .isNotNull();
        assertThat(jdbc.queryForObject("""
                SELECT actor_tipo FROM evento_auditoria WHERE entidad_id = ? AND accion = 'TURNO_REPROGRAMADO'""",
                String.class, turno)).isEqualTo("PACIENTE");
        ver(token).andExpect(status().isOk());
    }

    @Test
    void reprogramarAlHuecoTomadoConservaElHorario() throws Exception {
        String token = reservarYObtenerToken(lunesA("10:00"));
        reservarYObtenerToken(lunesA("11:00"));

        JsonNode error = cuerpo(reprogramar(token, lunesA("11:00")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("HUECO_NO_DISPONIBLE")));

        assertThat(huecosDe(error)).doesNotContain(lunesA("11:00").toString());
        ver(token).andExpect(jsonPath("$.turno.inicio").value(lunesA("10:00").toString()));
    }

    @Test
    void reprogramarAUnHorarioNoOfrecible() throws Exception {
        String token = reservarYObtenerToken(lunesA("10:00"));

        reprogramar(token, lunesA("14:00")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("HUECO_NO_DISPONIBLE"));
        reprogramar(token, reloj.instant().plusSeconds(60)).andExpect(status().isConflict());
        ver(token).andExpect(jsonPath("$.turno.inicio").value(lunesA("10:00").toString()));
    }

    @Test
    void reprogramarFueraDePlazo() throws Exception {
        String token = reservarYObtenerToken(lunesA("10:00"));
        plazoVencidoPara(token);

        reprogramar(token, lunesA("11:00")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("FUERA_DE_PLAZO"));
    }

    @Test
    void conReservaDesactivadaNoSeReprogramaPeroSeCancela() throws Exception {
        String token = reservarYObtenerToken(lunesA("10:00"));
        desactivarReserva();

        ver(token).andExpect(jsonPath("$.puedeModificar").value(true)).andExpect(jsonPath("$.puedeReprogramar").value(false));
        reprogramar(token, lunesA("11:00")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("RESERVA_NO_DISPONIBLE"))
                .andExpect(jsonPath("$.telefonoContacto").value(TELEFONO_CONSULTORIO));
        huecos(token).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("RESERVA_NO_DISPONIBLE"));
        ver(token).andExpect(jsonPath("$.turno.inicio").value(lunesA("10:00").toString()));
        cancelar(token).andExpect(status().isOk());
    }

    @Test
    void elTokenDeOtroConsultorioNoCambiaNadaDelPropio() throws Exception {
        String token = reservarYObtenerToken(lunesA("10:00"));
        AgendaDePrueba otra = AgendaDePrueba.nueva(jdbc);
        String otroSlug = prepararPublico(otra);
        Map<String, Object> pedidoOtro = pedido(lunesA("11:00"), AgendaDePrueba.dniNuevo());
        pedidoOtro.put("prestacionId", otra.prestacion("Consulta", 30, true).toString());
        String tokenOtro = json.readTree(reservar(otroSlug, pedidoOtro).andReturn().getResponse().getContentAsString())
                .path("linkGestion").asText().substring(3);

        cancelar(tokenOtro).andExpect(status().isOk());

        ver(token).andExpect(jsonPath("$.turno.estado").value("RESERVADO"));
        assertThat(jdbc.queryForObject("SELECT consultorio_id FROM turno WHERE id = ?", UUID.class, turnoDe(tokenOtro)))
                .isEqualTo(otra.consultorio());
    }

    @Test
    void operacionesConTokenInexistenteSon404() throws Exception {
        cancelar(TOKEN_INEXISTENTE).andExpect(status().isNotFound());
        reprogramar(TOKEN_INEXISTENTE, lunesA("11:00")).andExpect(status().isNotFound());
        huecos(TOKEN_INEXISTENTE).andExpect(status().isNotFound());
        cancelar("malformado").andExpect(status().isNotFound());
    }

    private ResultActions huecos(String token) throws Exception {
        Instant dia = lunesA("00:00");
        String fecha = dia.atZone(ZONA).toLocalDate().toString();
        return mvc.perform(get(RUTA_TURNOS + token + "/huecos").param("desde", fecha).param("hasta", fecha));
    }
}
