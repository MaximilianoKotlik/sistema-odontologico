package ar.sistemaodontologico.agenda.web;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import pruebas.agenda.AgendaDePrueba;

/**
 * Escrituras de turnos por la API (C-12, tareas 3.1 a 3.4; spec api-de-turnos-y-bloqueos, design D6 y D10). La grilla
 * de prueba atiende de lunes a viernes de 09:00 a 13:00; los tests corren en la semana de {@link AgendaDePrueba#LUNES}.
 */
class EscriturasDeTurnosTest extends ApiDeAgendaTest {

    private static final String TURNOS = "/api/v1/turnos";

    private UUID consulta;
    private UUID ana;

    @BeforeEach
    void cargarDatos() {
        consulta = agenda.prestacion("Consulta", 30, true);
        ana = agenda.paciente("Ana", "Prueba", "90000001", "+5491100000001", "ana@example.com");
    }

    // --- Crear (3.1) ---

    @Test
    void turnoParaUnPacienteExistente() throws Exception {
        crear(Map.of("pacienteId", ana, "prestacionId", consulta, "inicio", lunesA("10:00"), "esSobreturno", false))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pacienteReutilizado").value(false))
                .andExpect(jsonPath("$.turno.id").isNotEmpty())
                .andExpect(jsonPath("$.turno.estado").value("RESERVADO"))
                .andExpect(jsonPath("$.turno.origen").value("MANUAL"))
                .andExpect(jsonPath("$.turno.inicio").value("2026-11-23T13:00:00Z"))
                .andExpect(jsonPath("$.turno.fin").value("2026-11-23T13:30:00Z"))
                .andExpect(jsonPath("$.turno.paciente.id").value(ana.toString()))
                .andExpect(jsonPath("$.turno.version").value(0));
    }

    @Test
    void sobreturnoConOtraDuracion() throws Exception {
        crear(Map.of("pacienteId", ana, "prestacionId", consulta, "inicio", lunesA("10:00"), "esSobreturno", false));

        crear(Map.of("pacienteId", ana, "prestacionId", consulta, "inicio", lunesA("10:00"), "duracionMin", 15,
                "esSobreturno", true))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.turno.esSobreturno").value(true))
                .andExpect(jsonPath("$.turno.duracionMin").value(15));
    }

    @Test
    void turnoConAltaRapida() throws Exception {
        String respuesta = crear(Map.of("pacienteNuevo", nuevo("Ana", "Prueba", "90.000.009"), "prestacionId",
                consulta, "inicio", lunesA("10:00"), "esSobreturno", false))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pacienteReutilizado").value(false))
                .andExpect(jsonPath("$.turno.estado").value("RESERVADO"))
                .andExpect(jsonPath("$.turno.paciente.dni").value("90000009"))
                .andReturn().getResponse().getContentAsString();

        UUID paciente = UUID.fromString(json.readTree(respuesta).get("turno").get("paciente").get("id").asText());
        assertThat(accionesAuditadas(paciente)).containsExactly("PACIENTE_CREADO");
    }

    @Test
    void altaRapidaConUnDniExistente() throws Exception {
        crear(Map.of("pacienteNuevo", nuevo("Ana", "Prueba", "90000001", "+54 9 11 0000-0077"), "prestacionId",
                consulta, "inicio", lunesA("10:00"), "esSobreturno", false))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pacienteReutilizado").value(true))
                .andExpect(jsonPath("$.turno.paciente.id").value(ana.toString()))
                .andExpect(jsonPath("$.turno.paciente.telefono").value("+5491100000001"))
                .andExpect(jsonPath("$.turno.paciente.requiereRevision").value(false));
    }

    @Test
    void altaRapidaConUnDniExistenteYOtroNombre() throws Exception {
        String respuesta = crear(Map.of("pacienteNuevo", nuevo("Anna", "Prueba", "90000001"), "prestacionId",
                consulta, "inicio", lunesA("10:00"), "esSobreturno", false))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pacienteReutilizado").value(true))
                .andExpect(jsonPath("$.turno.paciente.nombre").value("Ana"))
                .andExpect(jsonPath("$.turno.paciente.requiereRevision").value(true))
                .andReturn().getResponse().getContentAsString();

        UUID turno = UUID.fromString(json.readTree(respuesta).get("turno").get("id").asText());
        Map<String, Object> paciente = jdbc.queryForMap(
                "SELECT nombre, nombre_informado, informado_turno_id FROM paciente WHERE id = ?", ana);
        assertThat(paciente).containsEntry("nombre", "Ana").containsEntry("nombre_informado", "Anna")
                .containsEntry("informado_turno_id", turno);
    }

    @Test
    void pacienteObligatorioYUnoSolo() throws Exception {
        crear(Map.of("prestacionId", consulta, "inicio", lunesA("10:00"), "esSobreturno", false))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
        crear(Map.of("pacienteId", ana, "pacienteNuevo", nuevo("Ana", "Prueba", "90000009"), "prestacionId",
                consulta, "inicio", lunesA("10:00"), "esSobreturno", false))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    @Test
    void altaRapidaConUnDniInvalidoNoCreaNada() throws Exception {
        crear(Map.of("pacienteNuevo", nuevo("Ana", "Prueba", "12"), "prestacionId", consulta, "inicio",
                lunesA("10:00"), "esSobreturno", false))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DNI_INVALIDO"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM turno WHERE consultorio_id = ?", Long.class,
                agenda.consultorio())).isZero();
    }

    @Test
    void unTurnoQueFallaNoDejaElPacienteNuevo() throws Exception {
        crear(Map.of("pacienteNuevo", nuevo("Ana", "Prueba", "90000009"), "prestacionId", consulta, "inicio",
                lunesA("18:00"), "esSobreturno", false))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("FUERA_DE_DISPONIBILIDAD"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM paciente WHERE consultorio_id = ? AND dni = '90000009'",
                Long.class, agenda.consultorio())).isZero();
    }

    @Test
    void pacienteOPrestacionDeOtroConsultorio() throws Exception {
        AgendaDePrueba deB = otraAgenda();
        crear(Map.of("pacienteId", deB.paciente("Carla", "Ficticia"), "prestacionId", consulta, "inicio",
                lunesA("10:00"), "esSobreturno", false))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
        crear(Map.of("pacienteId", ana, "prestacionId", deB.prestacion("Consulta", 30, true), "inicio",
                lunesA("10:00"), "esSobreturno", false))
                .andExpect(status().isNotFound());
    }

    // --- Cambiar (3.2) ---

    @Test
    void reprogramar() throws Exception {
        agenda.franja(1, "14:00", "18:00");
        UUID turno = turnoDeLas10();

        pedirPost(TURNOS + "/" + turno + "/reprogramar", Map.of("inicio", lunesA("16:00"), "version", 0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(turno.toString()))
                .andExpect(jsonPath("$.inicio").value("2026-11-23T19:00:00Z"))
                .andExpect(jsonPath("$.fin").value("2026-11-23T19:30:00Z"))
                .andExpect(jsonPath("$.version").value(1));
        assertThat(accionesAuditadas(turno)).containsExactly("TURNO_REPROGRAMADO");
    }

    @Test
    void confirmarYCancelarConMotivo() throws Exception {
        UUID turno = turnoDeLas10();

        cambiarEstado(turno, Map.of("accion", "CONFIRMAR", "version", 0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONFIRMADO"))
                .andExpect(jsonPath("$.version").value(1));
        cambiarEstado(turno, Map.of("accion", "CANCELAR", "motivo", "Viaja", "version", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADO"))
                .andExpect(jsonPath("$.canceladoPor").value("ODONTOLOGO"))
                .andExpect(jsonPath("$.motivoCancelacion").value("Viaja"));
        assertThat(accionesAuditadas(turno)).containsExactly("TURNO_ESTADO_CAMBIADO", "TURNO_ESTADO_CAMBIADO");
    }

    @Test
    void marcarAusenteUnTurnoQueYaEmpezo() throws Exception {
        Instant haceUnaHora = Instant.ofEpochSecond(
                Math.floorDiv(Clock.systemUTC().instant().minus(Duration.ofHours(1)).getEpochSecond(), 300) * 300);
        UUID turno = agenda.turno(ana, consulta, haceUnaHora, 30, "CONFIRMADO", false);

        cambiarEstado(turno, Map.of("accion", "MARCAR_AUSENTE", "version", 0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("AUSENTE"));
    }

    @Test
    void transicionInvalida() throws Exception {
        UUID turno = agenda.turno(ana, consulta, lunesA("10:00"), 30, "CANCELADO", false);

        cambiarEstado(turno, Map.of("accion", "CONFIRMAR", "version", 0))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));
    }

    @Test
    void ajustarLaDuracion() throws Exception {
        UUID turno = turnoDeLas10();

        pedirPatch(TURNOS + "/" + turno + "/duracion", Map.of("duracionMin", 45, "version", 0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duracionMin").value(45))
                .andExpect(jsonPath("$.fin").value("2026-11-23T13:45:00Z"));
    }

    @Test
    void duracionNoMultiploDe5() throws Exception {
        UUID turno = turnoDeLas10();

        pedirPatch(TURNOS + "/" + turno + "/duracion", Map.of("duracionMin", 47, "version", 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DURACION_INVALIDA"));
    }

    @Test
    void editarLaNota() throws Exception {
        UUID turno = turnoDeLas10();

        pedirPatch(TURNOS + "/" + turno + "/nota", Map.of("nota", "Trae estudios", "version", 0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notaInterna").value("Trae estudios"))
                .andExpect(jsonPath("$.tieneNota").value(true));
    }

    @Test
    void notaDe501Caracteres() throws Exception {
        UUID turno = turnoDeLas10();

        pedirPatch(TURNOS + "/" + turno + "/nota", Map.of("nota", "a".repeat(501), "version", 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    @Test
    void versionVieja() throws Exception {
        UUID turno = turnoDeLas10();
        pedirPatch(TURNOS + "/" + turno + "/nota", Map.of("nota", "Primera", "version", 0)).andExpect(status().isOk());

        pedirPatch(TURNOS + "/" + turno + "/nota", Map.of("nota", "Segunda", "version", 0))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO_CONCURRENCIA"));
    }

    @Test
    void sinVersion() throws Exception {
        UUID turno = turnoDeLas10();

        pedirPatch(TURNOS + "/" + turno + "/nota", Map.of("nota", "Trae estudios"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    @Test
    void cambiarUnTurnoAjeno() throws Exception {
        AgendaDePrueba deB = otraAgenda();
        UUID turno = deB.turno(deB.paciente("Carla", "Ficticia"), deB.prestacion("Consulta", 30, true),
                lunesA("10:00"), 30, "RESERVADO", false);

        cambiarEstado(turno, Map.of("accion", "CANCELAR", "version", 0))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
        pedirPost(TURNOS + "/" + turno + "/reprogramar", Map.of("inicio", lunesA("11:00"), "version", 0))
                .andExpect(status().isNotFound());
        pedirPatch(TURNOS + "/" + turno + "/duracion", Map.of("duracionMin", 45, "version", 0))
                .andExpect(status().isNotFound());
        pedirPatch(TURNOS + "/" + turno + "/nota", Map.of("nota", "Ajena", "version", 0))
                .andExpect(status().isNotFound());

        assertThat(filaDeTurno(turno)).containsEntry("estado", "RESERVADO").containsEntry("version", 0L);
    }

    // --- Solapamiento (3.3) ---

    @Test
    void solapamientoConUnTurno() throws Exception {
        UUID existente = turnoDeLas10();
        agenda.turno(ana, consulta, lunesA("10:30"), 30, "RESERVADO", false);

        String respuesta = crear(Map.of("pacienteId", agenda.paciente("Carla", "Ficticia"), "prestacionId",
                consulta, "inicio", lunesA("10:15"), "duracionMin", 30, "esSobreturno", false))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("TURNO_SOLAPADO"))
                .andExpect(jsonPath("$.turnoEnConflicto.id").value(existente.toString()))
                .andExpect(jsonPath("$.turnoEnConflicto.inicio").value("2026-11-23T13:00:00Z"))
                .andExpect(jsonPath("$.turnoEnConflicto.fin").value("2026-11-23T13:30:00Z"))
                .andExpect(jsonPath("$.turnosEnConflicto.length()").value(2))
                .andReturn().getResponse().getContentAsString();

        assertThat(respuesta).doesNotContain("Ana", "Prueba", "Carla", "90000001");
    }

    // --- Errores con su estado HTTP (3.4) ---

    @Test
    void erroresDeLaAgenda() throws Exception {
        agenda.bloqueo(lunesA("12:00"), lunesA("13:00"), null);
        UUID inactiva = agenda.prestacion("Blanqueamiento", 60, false);

        crear(Map.of("pacienteId", ana, "prestacionId", consulta, "inicio", lunesA("12:00"), "esSobreturno", false))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("HORARIO_BLOQUEADO"));
        crear(Map.of("pacienteId", ana, "prestacionId", inactiva, "inicio", lunesA("10:00"), "esSobreturno", false))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PRESTACION_INACTIVA"));
        crear(Map.of("pacienteId", ana, "prestacionId", consulta, "inicio", lunesA("10:03"), "esSobreturno", false))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("HORARIO_INVALIDO"));
        crear(Map.of("pacienteId", ana, "prestacionId", consulta, "inicio", lunesA("10:00"), "duracionMin", 0,
                "esSobreturno", false))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DURACION_INVALIDA"));
        cambiarEstado(UUID.randomUUID(), Map.of("accion", "CONFIRMAR", "version", 0))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }

    private UUID turnoDeLas10() {
        return agenda.turno(ana, consulta, lunesA("10:00"), 30, "RESERVADO", false);
    }

    private ResultActions crear(Map<String, Object> cuerpo) throws Exception {
        return pedirPost(TURNOS, cuerpo);
    }

    private ResultActions cambiarEstado(UUID turno, Map<String, Object> cuerpo) throws Exception {
        return pedirPatch(TURNOS + "/" + turno + "/estado", cuerpo);
    }

    private static Map<String, Object> nuevo(String nombre, String apellido, String dni) {
        return nuevo(nombre, apellido, dni, "+5491100000009");
    }

    private static Map<String, Object> nuevo(String nombre, String apellido, String dni, String telefono) {
        Map<String, Object> paciente = new HashMap<>();
        paciente.put("nombre", nombre);
        paciente.put("apellido", apellido);
        paciente.put("dni", dni);
        paciente.put("telefono", telefono);
        return paciente;
    }
}
