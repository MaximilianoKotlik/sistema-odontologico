package ar.sistemaodontologico.reserva.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.sistemaodontologico.compartido.application.PublicadorDeAuditoria;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import pruebas.agenda.AgendaDePrueba;

/** Reserva pública (C-15, tareas 6.1 a 6.4, 6.6 y 8.4; spec reserva-publica). */
class ReservaPublicaTest extends ApiPublicaTest {

    @MockitoSpyBean
    private PublicadorDeAuditoria auditoria;

    @Test
    void reservaExitosa() throws Exception {
        String dni = AgendaDePrueba.dniNuevo();

        JsonNode respuesta = cuerpo(reservar(slug, pedido(lunesA("10:00"), dni)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.turno.estado").value("RESERVADO"))
                .andExpect(jsonPath("$.turno.prestacion").value("Consulta"))
                .andExpect(jsonPath("$.turno.inicio").value(lunesA("10:00").toString()))
                .andExpect(jsonPath("$.turno.fin").value(lunesA("10:30").toString())));

        assertThat(respuesta.path("linkGestion").asText()).matches("/t/[A-Za-z0-9_-]{43}");
        UUID turno = UUID.fromString(respuesta.path("turno").path("id").asText());
        Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM turno WHERE id = ?", turno);
        assertThat(fila).containsEntry("origen", "LINK").containsEntry("duracion_min", 30);
        assertThat(fila.get("novedad_paciente_at")).isNotNull();
        assertThat(jdbc.queryForObject("SELECT consentimiento_version FROM paciente WHERE dni = ? AND consultorio_id = ?",
                String.class, dni, agenda.consultorio())).isEqualTo("2026-11-borrador");
    }

    @Test
    void soloElHashQuedaEnLaBase() throws Exception {
        String token = reservarYObtenerToken(lunesA("10:00"));

        String hash = jdbc.queryForObject("SELECT token_hash FROM token_gestion_turno WHERE turno_id = ?", String.class,
                turnoDe(token));

        assertThat(hash).hasSize(64).matches("[0-9a-f]+").isNotEqualTo(token);
    }

    @Test
    void cadaReservaTieneSuToken() throws Exception {
        String uno = reservarYObtenerToken(lunesA("10:00"));
        String otro = reservarYObtenerToken(lunesA("11:00"));

        assertThat(uno).isNotEqualTo(otro);
        assertThat(Base64.getUrlDecoder().decode(uno)).hasSize(32);
    }

    @Test
    void sinAceptarLaPrivacidad() throws Exception {
        Map<String, Object> cuerpo = pedido(lunesA("10:00"), AgendaDePrueba.dniNuevo());
        cuerpo.put("aceptaPrivacidad", false);

        reservar(slug, cuerpo).andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value("aceptaPrivacidad"));
        assertThat(acciones()).isEmpty();
    }

    @Test
    void faltaUnDatoObligatorio() throws Exception {
        Map<String, Object> cuerpo = pedido(lunesA("10:00"), AgendaDePrueba.dniNuevo());
        cuerpo.remove("nombre");

        reservar(slug, cuerpo).andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    @Test
    void dniInvalido() throws Exception {
        reservar(slug, pedido(lunesA("10:00"), "12.34")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DNI_INVALIDO"));
    }

    @Test
    void telefonoInvalido() throws Exception {
        Map<String, Object> cuerpo = pedido(lunesA("10:00"), AgendaDePrueba.dniNuevo());
        cuerpo.put("telefono", "123");

        reservar(slug, cuerpo).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("TELEFONO_INVALIDO"));
    }

    @Test
    void elConsultorioDelBodySeIgnora() throws Exception {
        AgendaDePrueba otra = AgendaDePrueba.nueva(jdbc);
        Map<String, Object> cuerpo = pedido(lunesA("10:00"), AgendaDePrueba.dniNuevo());
        cuerpo.put("consultorioId", otra.consultorio().toString());

        reservar(slug, cuerpo).andExpect(status().isCreated());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM turno WHERE consultorio_id = ?", Long.class,
                otra.consultorio())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM turno WHERE consultorio_id = ?", Long.class,
                agenda.consultorio())).isEqualTo(1);
    }

    @Test
    void prestacionDeOtroConsultorioEs404() throws Exception {
        Map<String, Object> cuerpo = pedido(lunesA("10:00"), AgendaDePrueba.dniNuevo());
        cuerpo.put("prestacionId", AgendaDePrueba.nueva(jdbc).prestacion("Consulta", 30, true).toString());

        reservar(slug, cuerpo).andExpect(status().isNotFound());
    }

    @Test
    void horariosNoOfrecibles() throws Exception {
        Instant fueraDeDisponibilidad = lunesA("14:00");
        Instant fueraDelPaso = lunesA("10:05");
        Instant sinAnticipacion = reloj.instant().plusSeconds(60);
        agenda.bloqueo(lunesA("11:00"), lunesA("12:00"), "Reunión");

        for (Instant inicio : List.of(fueraDeDisponibilidad, fueraDelPaso, sinAnticipacion, lunesA("11:00"))) {
            reservar(slug, pedido(inicio, AgendaDePrueba.dniNuevo())).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("HUECO_NO_DISPONIBLE"));
        }
        assertThat(acciones()).isEmpty();
    }

    @Test
    void elHuecoTomadoDevuelveLosHuecosDelDia() throws Exception {
        reservarYObtenerToken(lunesA("10:00"));

        JsonNode error = cuerpo(reservar(slug, pedido(lunesA("10:00"), AgendaDePrueba.dniNuevo()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("HUECO_NO_DISPONIBLE")));

        assertThat(huecosDe(error)).contains(lunesA("09:00").toString()).doesNotContain(lunesA("10:00").toString());
    }

    @Test
    void conReservaDesactivadaNoCreaNada() throws Exception {
        desactivarReserva();

        reservar(slug, pedido(lunesA("10:00"), AgendaDePrueba.dniNuevo())).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("RESERVA_NO_DISPONIBLE"))
                .andExpect(jsonPath("$.telefonoContacto").value(TELEFONO_CONSULTORIO));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM paciente WHERE consultorio_id = ?", Long.class,
                agenda.consultorio())).isZero();
        assertThat(acciones()).isEmpty();
    }

    @Test
    void dniConPuntosYaRegistrado() throws Exception {
        String dni = AgendaDePrueba.dniNuevo();
        reservar(slug, pedido(lunesA("10:00"), dni)).andExpect(status().isCreated());
        String conPuntos = dni.charAt(0) + dni.substring(1, 2) + "." + dni.substring(2, 5) + "." + dni.substring(5);

        reservar(slug, pedido(lunesA("11:00"), conPuntos)).andExpect(status().isCreated());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM paciente WHERE consultorio_id = ?", Long.class,
                agenda.consultorio())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT requiere_revision FROM paciente WHERE dni = ? AND consultorio_id = ?",
                Boolean.class, dni, agenda.consultorio())).isFalse();
    }

    @Test
    void mismoDniConOtroNombreSeMarcaSinPisarDatos() throws Exception {
        String dni = AgendaDePrueba.dniNuevo();
        reservar(slug, pedido(lunesA("10:00"), dni)).andExpect(status().isCreated());
        Map<String, Object> otro = pedido(lunesA("11:00"), dni);
        otro.put("nombre", "Otra");
        otro.put("apellido", "Persona");

        JsonNode respuesta = cuerpo(reservar(slug, otro).andExpect(status().isCreated()));

        Map<String, Object> paciente = jdbc.queryForMap("""
                SELECT nombre, requiere_revision, nombre_informado, informado_turno_id
                  FROM paciente WHERE dni = ? AND consultorio_id = ?""", dni, agenda.consultorio());
        assertThat(paciente).containsEntry("nombre", "Paciente").containsEntry("requiere_revision", true)
                .containsEntry("nombre_informado", "Otra")
                .containsEntry("informado_turno_id", UUID.fromString(respuesta.path("turno").path("id").asText()));
        assertThat(acciones()).contains("PACIENTE_MARCADO_PARA_REVISION");
    }

    @Test
    void mismoDniEnOtroConsultorioEsOtroPaciente() throws Exception {
        String dni = AgendaDePrueba.dniNuevo();
        AgendaDePrueba otra = AgendaDePrueba.nueva(jdbc);
        String otroSlug = prepararPublico(otra);
        Map<String, Object> enOtro = pedido(lunesA("10:00"), dni);
        enOtro.put("prestacionId", otra.prestacion("Consulta", 30, true).toString());
        reservar(otroSlug, enOtro).andExpect(status().isCreated());

        reservar(slug, pedido(lunesA("10:00"), dni)).andExpect(status().isCreated());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM paciente WHERE dni = ?", Long.class, dni)).isEqualTo(2);
    }

    @Test
    void elTercerTurnoSuperaElLimite() throws Exception {
        String dni = AgendaDePrueba.dniNuevo();
        reservar(slug, pedido(lunesA("09:00"), dni)).andExpect(status().isCreated());
        reservar(slug, pedido(lunesA("10:00"), dni)).andExpect(status().isCreated());

        reservar(slug, pedido(lunesA("11:00"), dni)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("LIMITE_TURNOS"));
    }

    @Test
    void turnosQueNoCuentanParaElLimite() throws Exception {
        String dni = AgendaDePrueba.dniNuevo();
        String cancelado = json.readTree(reservar(slug, pedido(lunesA("09:00"), dni)).andReturn().getResponse()
                .getContentAsString()).path("linkGestion").asText().substring(3);
        cancelar(cancelado).andExpect(status().isOk());
        UUID paciente = jdbc.queryForObject("SELECT id FROM paciente WHERE dni = ? AND consultorio_id = ?", UUID.class,
                dni, agenda.consultorio());
        agenda.turno(paciente, consulta, lunesA("09:30"), 30, "RESERVADO", false); // manual
        agenda.turno(paciente, consulta, reloj.instant().minusSeconds(86_400).truncatedTo(java.time.temporal.ChronoUnit.HOURS),
                30, "RESERVADO", false); // pasado
        reservar(slug, pedido(lunesA("10:00"), dni)).andExpect(status().isCreated());

        reservar(slug, pedido(lunesA("11:00"), dni)).andExpect(status().isCreated());
    }

    @Test
    void elHuecoOcupadoSeInformaAntesQueElLimite() throws Exception {
        String dni = AgendaDePrueba.dniNuevo();
        reservar(slug, pedido(lunesA("09:00"), dni)).andExpect(status().isCreated());
        reservar(slug, pedido(lunesA("10:00"), dni)).andExpect(status().isCreated());

        reservar(slug, pedido(lunesA("10:00"), dni)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("HUECO_NO_DISPONIBLE"));
    }

    @Test
    void eventosDeUnPacienteNuevoSinDatosPersonales() throws Exception {
        String dni = AgendaDePrueba.dniNuevo();

        String token = json.readTree(reservar(slug, pedido(lunesA("10:00"), dni)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).path("linkGestion").asText().substring(3);

        assertThat(acciones()).containsExactly("PACIENTE_CREADO", "TURNO_CREADO");
        UUID paciente = jdbc.queryForObject("SELECT id FROM paciente WHERE dni = ? AND consultorio_id = ?", UUID.class,
                dni, agenda.consultorio());
        List<Map<String, Object>> eventos = jdbc.queryForList("""
                SELECT actor_tipo, actor_id, coalesce(valor_anterior::text, '') || valor_nuevo::text AS valores
                  FROM evento_auditoria WHERE consultorio_id = ?""", agenda.consultorio());
        assertThat(eventos).allSatisfy(evento -> {
            assertThat(evento).containsEntry("actor_tipo", "PACIENTE").containsEntry("actor_id", paciente);
            assertThat((String) evento.get("valores")).doesNotContain(dni, token, "Paciente", "+549");
        });
    }

    @Test
    void reservarDeNuevoActualizaElConsentimientoYLoAudita() throws Exception {
        String dni = AgendaDePrueba.dniNuevo();
        reservar(slug, pedido(lunesA("10:00"), dni)).andExpect(status().isCreated());
        jdbc.update("UPDATE paciente SET consentimiento_version = '2025-vieja' WHERE dni = ? AND consultorio_id = ?", dni,
                agenda.consultorio());

        reservar(slug, pedido(lunesA("11:00"), dni)).andExpect(status().isCreated());

        assertThat(jdbc.queryForObject("SELECT consentimiento_version FROM paciente WHERE dni = ? AND consultorio_id = ?",
                String.class, dni, agenda.consultorio())).isEqualTo("2026-11-borrador");
        assertThat(acciones()).contains("PACIENTE_CONSENTIMIENTO_ACTUALIZADO");
    }

    @Test
    void siFallaLaAuditoriaNoQuedaNada() throws Exception {
        doThrow(new IllegalStateException("falla de auditoría")).when(auditoria)
                .publicar(argThat(cambio -> cambio != null && "TURNO_CREADO".equals(cambio.accion())));
        try {
            reservar(slug, pedido(lunesA("10:00"), AgendaDePrueba.dniNuevo())).andExpect(status().isInternalServerError());
        } finally {
            doCallRealMethod().when(auditoria).publicar(any());
        }

        assertThat(jdbc.queryForObject("SELECT count(*) FROM turno WHERE consultorio_id = ?", Long.class,
                agenda.consultorio())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM paciente WHERE consultorio_id = ?", Long.class,
                agenda.consultorio())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM token_gestion_turno WHERE consultorio_id = ?", Long.class,
                agenda.consultorio())).isZero();
    }
}
