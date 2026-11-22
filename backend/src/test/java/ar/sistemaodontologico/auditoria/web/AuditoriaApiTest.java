package ar.sistemaodontologico.auditoria.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pruebas.agenda.AgendaDePrueba.lunesA;

import ar.sistemaodontologico.compartido.IntegracionPostgresTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import pruebas.agenda.AgendaDePrueba;
import pruebas.seguridad.TokensDePrueba;

/** Historial del turno y registro del consultorio (C-18, tareas 3.1 a 4.4), con PostgreSQL real y datos ficticios. */
@AutoConfigureMockMvc
class AuditoriaApiTest extends IntegracionPostgresTest {

    private static final String REGISTRO = "/api/v1/auditoria";
    private static final Instant BASE = Instant.parse("2026-11-10T15:00:00Z");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper json;

    private AgendaDePrueba agenda;
    private UUID prestacion;
    private UUID paciente;
    private UUID turno;

    @BeforeEach
    void datos() {
        agenda = AgendaDePrueba.nueva(jdbc);
        prestacion = agenda.prestacion("Consulta", 30, true);
        paciente = agenda.paciente("Paciente", "Uno");
        turno = agenda.turno(paciente, prestacion, lunesA("10:00"), 30, "RESERVADO", false);
    }

    @Test
    void historialEnOrdenCronologicoConActoresYValores() throws Exception {
        evento(agenda, "PACIENTE", paciente, "turno", turno, "TURNO_CREADO", BASE, null, "{\"estado\":\"RESERVADO\"}");
        evento(agenda, "ODONTOLOGO", agenda.usuario(), "turno", turno, "TURNO_REPROGRAMADO", BASE.plusSeconds(60),
                "{\"inicio\":\"2026-11-23T13:00:00Z\"}", "{\"inicio\":\"2026-11-23T14:00:00Z\"}");

        pedir(historial(turno), agenda).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].accion").value("TURNO_CREADO"))
                .andExpect(jsonPath("$[0].actor.tipo").value("PACIENTE"))
                .andExpect(jsonPath("$[0].actor.nombre").value("Paciente Uno"))
                .andExpect(jsonPath("$[0].actor.identidadEnRevision").value(false))
                .andExpect(jsonPath("$[0].valorNuevo.estado").value("RESERVADO"))
                .andExpect(jsonPath("$[0].contexto.paciente").value("Paciente Uno"))
                .andExpect(jsonPath("$[1].accion").value("TURNO_REPROGRAMADO"))
                .andExpect(jsonPath("$[1].actor.tipo").value("ODONTOLOGO"))
                .andExpect(jsonPath("$[1].actor.nombre").value("Ana Prueba"))
                .andExpect(jsonPath("$[1].valorAnterior.inicio").value("2026-11-23T13:00:00Z"))
                .andExpect(jsonPath("$[1].valorNuevo.inicio").value("2026-11-23T14:00:00Z"));
    }

    @Test
    void historialDeUnTurnoDeOtroConsultorioEs404() throws Exception {
        AgendaDePrueba otra = AgendaDePrueba.nueva(jdbc);

        pedir(historial(turno), otra).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }

    @Test
    void historialDeUnTurnoInexistenteEs404() throws Exception {
        pedir(historial(UUID.randomUUID()), agenda).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }

    @Test
    void historialDeUnTurnoSinEventosEsListaVacia() throws Exception {
        pedir(historial(turno), agenda).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void reservaConDniEnRevisionNoMuestraElNombreDeLaFicha() throws Exception {
        evento(agenda, "PACIENTE", paciente, "turno", turno, "TURNO_CREADO", BASE, null, null);
        jdbc.update("""
                UPDATE paciente SET requiere_revision = true, nombre_informado = 'Otro', apellido_informado = 'Nombre',
                       informado_at = ?, informado_turno_id = ? WHERE id = ?""", Timestamp.from(BASE), turno, paciente);

        pedir(historial(turno), agenda).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].actor.identidadEnRevision").value(true))
                .andExpect(jsonPath("$[0].actor.nombre").value(nullValue()))
                .andExpect(jsonPath("$[0].contexto.paciente").value("Paciente Uno"));
        pedir(REGISTRO, agenda).andExpect(status().isOk())
                .andExpect(jsonPath("$.eventos[0].actor.identidadEnRevision").value(true))
                .andExpect(jsonPath("$.eventos[0].actor.nombre").value(nullValue()));
    }

    @Test
    void registroSinFiltrosDelMasNuevoAlMasViejoConSuEntidad() throws Exception {
        UUID bloqueo = UUID.randomUUID();
        evento(agenda, "ODONTOLOGO", agenda.usuario(), "turno", turno, "TURNO_CREADO", BASE, null, null);
        evento(agenda, "ODONTOLOGO", agenda.usuario(), "bloqueo", bloqueo, "BLOQUEO_CREADO", BASE.plusSeconds(1), null, null);
        evento(agenda, "SISTEMA", null, "consultorio", agenda.consultorio(), "PLAN_VENCIDO", BASE.plusSeconds(2), null, null);

        pedir(REGISTRO, agenda).andExpect(status().isOk())
                .andExpect(jsonPath("$.eventos[*].accion", contains("PLAN_VENCIDO", "BLOQUEO_CREADO", "TURNO_CREADO")))
                .andExpect(jsonPath("$.eventos[1].entidad").value("bloqueo"))
                .andExpect(jsonPath("$.eventos[1].entidadId").value(bloqueo.toString()))
                .andExpect(jsonPath("$.eventos[0].actor.tipo").value("SISTEMA"))
                .andExpect(jsonPath("$.eventos[0].actor.nombre").value(nullValue()))
                .andExpect(jsonPath("$.siguienteCursor").value(nullValue()));
    }

    @Test
    void registroNoTraeEventosDeOtroConsultorio() throws Exception {
        AgendaDePrueba otra = AgendaDePrueba.nueva(jdbc);
        evento(otra, "SISTEMA", null, "consultorio", otra.consultorio(), "AJENO", BASE, null, null);
        evento(agenda, "SISTEMA", null, "consultorio", agenda.consultorio(), "PROPIO", BASE, null, null);

        pedir(REGISTRO, agenda).andExpect(jsonPath("$.eventos[*].accion", contains("PROPIO")));
    }

    @Test
    void loginFallidoNoTraeActorNiNombrePeroSiIp() throws Exception {
        jdbc.update("""
                INSERT INTO evento_auditoria (id, consultorio_id, ocurrido_at, actor_tipo, entidad, entidad_id, accion, ip)
                VALUES (?, ?, ?, 'ODONTOLOGO', 'usuario', ?, 'LOGIN_FALLIDO', '203.0.113.10'::inet)""",
                UUID.randomUUID(), agenda.consultorio(), Timestamp.from(BASE), agenda.usuario());

        pedir(REGISTRO, agenda)
                .andExpect(jsonPath("$.eventos[0].actor.tipo").value("ODONTOLOGO"))
                .andExpect(jsonPath("$.eventos[0].actor.id").value(nullValue()))
                .andExpect(jsonPath("$.eventos[0].actor.nombre").value(nullValue()))
                .andExpect(jsonPath("$.eventos[0].ip").value("203.0.113.10"));
    }

    @Test
    void filtraPorTurno() throws Exception {
        UUID otroTurno = agenda.turno(paciente, prestacion, lunesA("11:00"), 30, "RESERVADO", false);
        evento(agenda, "SISTEMA", null, "turno", turno, "UNO", BASE, null, null);
        evento(agenda, "SISTEMA", null, "turno", otroTurno, "DOS", BASE, null, null);

        pedir(REGISTRO + "?turnoId=" + turno, agenda).andExpect(jsonPath("$.eventos[*].accion", contains("UNO")));
    }

    @Test
    void filtraPorRangoEnLaZonaDelConsultorio() throws Exception {
        evento(agenda, "SISTEMA", null, "turno", turno, "DIA_DIEZ_NOCHE", Instant.parse("2026-11-11T02:30:00Z"), null, null);
        evento(agenda, "SISTEMA", null, "turno", turno, "DIA_ONCE", Instant.parse("2026-11-11T03:00:00Z"), null, null);
        evento(agenda, "SISTEMA", null, "turno", turno, "DIA_NUEVE", Instant.parse("2026-11-10T02:59:59Z"), null, null);

        pedir(REGISTRO + "?desde=2026-11-10&hasta=2026-11-10", agenda)
                .andExpect(jsonPath("$.eventos[*].accion", contains("DIA_DIEZ_NOCHE")));
    }

    @Test
    void filtraPorVariasAcciones() throws Exception {
        evento(agenda, "SISTEMA", null, "turno", turno, "TURNO_CREADO", BASE, null, null);
        evento(agenda, "SISTEMA", null, "turno", turno, "TURNO_REPROGRAMADO", BASE.plusSeconds(1), null, null);
        evento(agenda, "SISTEMA", null, "turno", turno, "LOGIN_FALLIDO", BASE.plusSeconds(2), null, null);

        pedir(REGISTRO + "?accion=TURNO_REPROGRAMADO&accion=LOGIN_FALLIDO", agenda)
                .andExpect(jsonPath("$.eventos[*].accion", contains("LOGIN_FALLIDO", "TURNO_REPROGRAMADO")));
    }

    @Test
    void filtrosCombinadosSinCoincidenciasDanListaVacia() throws Exception {
        evento(agenda, "SISTEMA", null, "turno", turno, "TURNO_CREADO", BASE, null, null);

        pedir(REGISTRO + "?turnoId=" + turno + "&desde=2020-01-01&hasta=2020-01-02&accion=TURNO_CREADO", agenda)
                .andExpect(status().isOk()).andExpect(jsonPath("$.eventos", hasSize(0)));
    }

    @Test
    void turnoDeOtroConsultorioEnElFiltroDaListaVacia() throws Exception {
        AgendaDePrueba otra = AgendaDePrueba.nueva(jdbc);
        evento(agenda, "SISTEMA", null, "turno", turno, "TURNO_CREADO", BASE, null, null);

        pedir(REGISTRO + "?turnoId=" + turno, otra).andExpect(status().isOk())
                .andExpect(jsonPath("$.eventos", hasSize(0)));
    }

    @ParameterizedTest
    @CsvSource({
        "desde=2026-11-20&hasta=2026-11-10, hasta",
        "desde=10-11-2026, desde",
        "turnoId=no-es-uuid, turnoId",
        "accion=turno_creado, accion",
        "tamano=0, tamano",
        "tamano=101, tamano",
        "tamano=muchos, tamano",
        "cursor=basura, cursor"
    })
    void filtrosInvalidosSon400ConSuCampo(String consulta, String campo) throws Exception {
        pedir(REGISTRO + "?" + consulta, agenda).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value(campo));
    }

    @Test
    void dosPaginasSinRepetirNiSaltear() throws Exception {
        insertarEventos(120);

        JsonNode primera = pagina(REGISTRO + "?tamano=100");
        assertThat(primera.get("eventos")).hasSize(100);
        assertThat(primera.get("siguienteCursor").isNull()).isFalse();
        JsonNode segunda = pagina(REGISTRO + "?tamano=100&cursor=" + primera.get("siguienteCursor").asText());

        assertThat(segunda.get("eventos")).hasSize(20);
        assertThat(segunda.get("siguienteCursor").isNull()).isTrue();
        Set<String> ids = new HashSet<>(idsDe(primera));
        ids.addAll(idsDe(segunda));
        assertThat(ids).hasSize(120);
    }

    @Test
    void porDefectoTrae50() throws Exception {
        insertarEventos(51);

        assertThat(pagina(REGISTRO).get("eventos")).hasSize(50);
    }

    @Test
    void unEventoNuevoEntrePaginasNoSeCuela() throws Exception {
        insertarEventos(3);
        JsonNode primera = pagina(REGISTRO + "?tamano=2");
        UUID nuevo = evento(agenda, "SISTEMA", null, "turno", turno, "NUEVO", BASE.plusSeconds(1000), null, null);

        JsonNode segunda = pagina(REGISTRO + "?tamano=2&cursor=" + primera.get("siguienteCursor").asText());

        assertThat(idsDe(segunda)).hasSize(1).doesNotContain(nuevo.toString()).doesNotContainAnyElementsOf(idsDe(primera));
    }

    @Test
    void unCursorDeOtroConsultorioEs400() throws Exception {
        insertarEventos(3);
        String cursor = pagina(REGISTRO + "?tamano=1").get("siguienteCursor").asText();

        pedir(REGISTRO + "?cursor=" + cursor, AgendaDePrueba.nueva(jdbc)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("cursor"));
    }

    @Test
    void conElPlanVencidoLeeSinAgregarEventos() throws Exception {
        evento(agenda, "SISTEMA", null, "turno", turno, "TURNO_CREADO", BASE, null, null);
        jdbc.update("UPDATE consultorio SET plan = 'VENCIDO' WHERE id = ?", agenda.consultorio());
        Long antes = cantidadDeEventos();

        pedir(REGISTRO, agenda).andExpect(status().isOk());
        pedir(historial(turno), agenda).andExpect(status().isOk());

        assertThat(cantidadDeEventos()).isEqualTo(antes);
    }

    @Test
    void sinSesionEs401() throws Exception {
        mvc.perform(get(REGISTRO)).andExpect(status().isUnauthorized());
        mvc.perform(get(historial(turno))).andExpect(status().isUnauthorized());
    }

    private static String historial(UUID id) {
        return "/api/v1/turnos/" + id + "/historial";
    }

    private ResultActions pedir(String ruta, AgendaDePrueba de) throws Exception {
        String token = TokensDePrueba.valido(SECRETO_JWT_DE_PRUEBA, de.usuario(), de.consultorio(), 1);
        return mvc.perform(get(ruta).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private JsonNode pagina(String ruta) throws Exception {
        return json.readTree(pedir(ruta, agenda).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static List<String> idsDe(JsonNode pagina) {
        List<String> ids = new ArrayList<>();
        pagina.get("eventos").forEach(evento -> ids.add(evento.get("id").asText()));
        return ids;
    }

    private void insertarEventos(int cantidad) {
        // Todos en el mismo instante: el id tiene que desempatar.
        jdbc.update("""
                INSERT INTO evento_auditoria (id, consultorio_id, ocurrido_at, actor_tipo, entidad, entidad_id, accion)
                SELECT gen_random_uuid(), ?, ?, 'SISTEMA', 'turno', ?, 'TURNO_CREADO' FROM generate_series(1, ?)""",
                agenda.consultorio(), Timestamp.from(BASE), turno, cantidad);
    }

    private Long cantidadDeEventos() {
        return jdbc.queryForObject("SELECT count(*) FROM evento_auditoria WHERE consultorio_id = ?", Long.class,
                agenda.consultorio());
    }

    private UUID evento(AgendaDePrueba de, String actorTipo, UUID actorId, String entidad, UUID entidadId, String accion,
            Instant ocurrido, String anterior, String nuevo) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO evento_auditoria (id, consultorio_id, ocurrido_at, actor_tipo, actor_id, entidad, entidad_id,
                                              accion, valor_anterior, valor_nuevo)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb))""",
                id, de.consultorio(), Timestamp.from(ocurrido), actorTipo, actorId, entidad, entidadId, accion, anterior,
                nuevo);
        return id;
    }
}
