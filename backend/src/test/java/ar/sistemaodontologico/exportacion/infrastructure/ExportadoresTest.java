package ar.sistemaodontologico.exportacion.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import ar.sistemaodontologico.compartido.IntegracionPostgresTest;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import ar.sistemaodontologico.exportacion.application.ExportadorDeEntidad;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Exportadores por entidad (C-19, tareas 3.1 y 3.2): lista blanca, tenant y relaciones, con datos ficticios. */
class ExportadoresTest extends IntegracionPostgresTest {

    private static final Instant INICIO = Instant.parse("2030-03-04T13:00:00Z");
    private static final String HASH_FICTICIO = "$2a$10$hash-ficticio-que-no-debe-exportarse-000000000000000000";

    @Autowired
    private List<ExportadorDeEntidad> exportadores;

    @Autowired
    private JdbcClient jdbc;

    private UUID consultorioA;
    private UUID consultorioB;
    private UUID pacienteA1;
    private UUID pacienteA2;
    private UUID prestacionA;
    private UUID turnoA1;
    private UUID turnoA2;
    private UUID turnoB;

    @BeforeEach
    void datos() {
        consultorioA = consultorio();
        consultorioB = consultorio();
        usuario(consultorioA);
        usuario(consultorioB);
        prestacionA = prestacion(consultorioA, "Consulta A");
        UUID prestacionB = prestacion(consultorioB, "Consulta B");
        disponibilidad(consultorioA);
        disponibilidad(consultorioB);
        pacienteA1 = paciente(consultorioA, "Uno", "10000001");
        pacienteA2 = paciente(consultorioA, "Dos", "10000002");
        UUID pacienteB = paciente(consultorioB, "OtroConsultorio", "10000003");
        turnoA1 = turno(consultorioA, pacienteA1, prestacionA, INICIO, "RESERVADO");
        turnoA2 = turno(consultorioA, pacienteA2, prestacionA, INICIO.plusSeconds(3600), "CANCELADO");
        turnoB = turno(consultorioB, pacienteB, prestacionB, INICIO, "RESERVADO");
        bloqueo(consultorioA);
        bloqueo(consultorioB);
        evento(consultorioA, turnoA1);
        evento(consultorioB, turnoB);
    }

    @Test
    void hayUnExportadorPorCadaUnaDeLasOchoEntidades() {
        assertThat(exportadores.stream().map(ExportadorDeEntidad::entidad)).containsExactlyInAnyOrder(
                "consultorio", "odontologo", "prestaciones", "disponibilidad", "bloqueos", "pacientes", "turnos",
                "auditoria");
    }

    @Test
    void cadaEntidadTraeSoloLasFilasDelConsultorio() throws IOException {
        Map<String, Integer> esperadas = Map.of("consultorio", 1, "odontologo", 1, "prestaciones", 1,
                "disponibilidad", 1, "bloqueos", 1, "pacientes", 2, "turnos", 2, "auditoria", 1);

        for (ExportadorDeEntidad exportador : exportadores) {
            List<Map<String, Object>> filas = leer(exportador.entidad(), consultorioA);
            assertThat(filas).as(exportador.entidad()).hasSize(esperadas.get(exportador.entidad()));
            assertThat(filas.toString()).as(exportador.entidad())
                    .doesNotContain(consultorioB.toString(), turnoB.toString(), "OtroConsultorio");
        }
    }

    @Test
    void devuelveLaCantidadDeFilas() throws IOException {
        ExportadorDeEntidad turnos = exportador("turnos");

        long cantidad = turnos.exportar(new ConsultorioId(consultorioA), fila -> { });

        assertThat(cantidad).isEqualTo(2);
    }

    @Test
    void turnoSeRelacionaConSuPacienteYPrestacion() throws IOException {
        Set<Object> pacientes = leer("pacientes", consultorioA).stream().map(f -> f.get("id")).collect(Collectors.toSet());
        Set<Object> prestaciones =
                leer("prestaciones", consultorioA).stream().map(f -> f.get("id")).collect(Collectors.toSet());

        List<Map<String, Object>> turnos = leer("turnos", consultorioA);

        assertThat(turnos).allSatisfy(turno -> {
            assertThat(pacientes).contains(turno.get("paciente_id"));
            assertThat(prestaciones).contains(turno.get("tipo_prestacion_id"));
        });
        assertThat(turnos.stream().map(f -> f.get("paciente_id"))).containsExactlyInAnyOrder(pacienteA1, pacienteA2);
    }

    @Test
    void incluyeTurnosCanceladosConSuEstado() throws IOException {
        assertThat(leer("turnos", consultorioA).stream().map(f -> f.get("estado")))
                .containsExactlyInAnyOrder("RESERVADO", "CANCELADO");
    }

    @Test
    void turnosNoIncluyenLaMarcaDeNovedad() {
        assertThat(exportador("turnos").columnas()).doesNotContain("novedad_paciente_at");
    }

    @Test
    void pacientesTraenConsentimientoRevisionYLoInformado() {
        assertThat(exportador("pacientes").columnas()).contains("id", "dni", "telefono_e164", "requiere_revision",
                "consentimiento_at", "consentimiento_version", "nombre_informado", "apellido_informado",
                "email_informado", "informado_at", "informado_turno_id");
    }

    @Test
    void instantesSonInstantYJsonbEsObjeto() throws IOException {
        Map<String, Object> turno = leer("turnos", consultorioA).stream()
                .filter(f -> turnoA1.equals(f.get("id"))).findFirst().orElseThrow();
        Map<String, Object> evento = leer("auditoria", consultorioA).getFirst();

        assertThat(turno.get("inicio")).isEqualTo(INICIO);
        assertThat(evento.get("valor_nuevo")).isInstanceOf(JsonNode.class);
        assertThat(((JsonNode) evento.get("valor_nuevo")).get("estado").asText()).isEqualTo("RESERVADO");
        assertThat(evento.get("ip")).isEqualTo("203.0.113.10");
    }

    @Test
    void ningunaListaBlancaTieneSecretos() {
        Set<String> prohibidas = Set.of("password_hash", "token_hash", "intentos_fallidos", "bloqueado_hasta",
                "credenciales_version", "password_cambiado_at");

        assertThat(exportadores).allSatisfy(e -> assertThat(e.columnas()).doesNotContainAnyElementsOf(prohibidas));
    }

    @Test
    void ningunExportadorLeeTablasDeTokens() {
        assertThat(exportadores).allSatisfy(e -> assertThat(((ExportadorJdbc) e).tabla())
                .isNotIn("refresh_token", "token_recuperacion", "token_gestion_turno"));
    }

    @Test
    void elOdontologoNoExportaSuHash() throws IOException {
        assertThat(leer("odontologo", consultorioA).toString()).doesNotContain(HASH_FICTICIO);
    }

    private List<Map<String, Object>> leer(String entidad, UUID consultorio) throws IOException {
        ExportadorDeEntidad exportador = exportador(entidad);
        List<Map<String, Object>> filas = new ArrayList<>();
        exportador.exportar(new ConsultorioId(consultorio), valores -> {
            Map<String, Object> fila = new LinkedHashMap<>();
            for (int i = 0; i < exportador.columnas().size(); i++) {
                fila.put(exportador.columnas().get(i), valores.get(i));
            }
            filas.add(fila);
        });
        return filas;
    }

    private ExportadorDeEntidad exportador(String entidad) {
        return exportadores.stream().filter(e -> e.entidad().equals(entidad)).findFirst().orElseThrow();
    }

    private UUID consultorio() {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO consultorio (id, nombre, slug, plan) VALUES (?, 'Consultorio de prueba', ?, 'PRUEBA')")
                .params(id, "exp-" + id.toString().substring(0, 8)).update();
        return id;
    }

    private void usuario(UUID consultorio) {
        Map<String, Object> valores = new HashMap<>();
        valores.put("id", UUID.randomUUID());
        valores.put("consultorio", consultorio);
        valores.put("email", "odontologo-" + consultorio + "@example.com");
        valores.put("hash", HASH_FICTICIO);
        valores.put("at", java.sql.Timestamp.from(INICIO));
        jdbc.sql("""
                INSERT INTO usuario (id, consultorio_id, email, password_hash, nombre, apellido, password_cambiado_at,
                                     terminos_version, terminos_aceptados_at)
                VALUES (:id, :consultorio, :email, :hash, 'Odonto', 'Prueba', :at, 'test', :at)""")
                .params(valores).update();
    }

    private UUID prestacion(UUID consultorio, String nombre) {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO tipo_prestacion (id, consultorio_id, nombre, duracion_min, orden) VALUES (?, ?, ?, 30, 1)")
                .params(id, consultorio, nombre).update();
        return id;
    }

    private void disponibilidad(UUID consultorio) {
        jdbc.sql("""
                INSERT INTO disponibilidad (id, consultorio_id, dia_semana, hora_inicio, hora_fin)
                VALUES (?, ?, 1, TIME '09:00', TIME '13:00')""").params(UUID.randomUUID(), consultorio).update();
    }

    private UUID paciente(UUID consultorio, String nombre, String dni) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO paciente (id, consultorio_id, nombre, apellido, dni, telefono_e164, email, origen)
                VALUES (?, ?, ?, 'Prueba', ?, '+5491100000001', 'paciente@example.com', 'MANUAL')""")
                .params(id, consultorio, nombre, dni).update();
        return id;
    }

    private UUID turno(UUID consultorio, UUID paciente, UUID prestacion, Instant inicio, String estado) {
        UUID id = UUID.randomUUID();
        String canceladoPor = "CANCELADO".equals(estado) ? "ODONTOLOGO" : null;
        Map<String, Object> valores = new HashMap<>();
        valores.put("id", id);
        valores.put("consultorio", consultorio);
        valores.put("paciente", paciente);
        valores.put("prestacion", prestacion);
        valores.put("inicio", java.sql.Timestamp.from(inicio));
        valores.put("fin", java.sql.Timestamp.from(inicio.plusSeconds(1800)));
        valores.put("estado", estado);
        valores.put("canceladoPor", canceladoPor);
        jdbc.sql("""
                INSERT INTO turno (id, consultorio_id, paciente_id, tipo_prestacion_id, inicio, fin, duracion_min, estado,
                                   origen, cancelado_por)
                VALUES (:id, :consultorio, :paciente, :prestacion, :inicio, :fin, 30, :estado, 'MANUAL',
                        CAST(:canceladoPor AS VARCHAR))""")
                .params(valores).update();
        return id;
    }

    private void bloqueo(UUID consultorio) {
        jdbc.sql("""
                INSERT INTO bloqueo (id, consultorio_id, inicio, fin, motivo)
                VALUES (?, ?, TIMESTAMPTZ '2030-03-05 13:00:00+00', TIMESTAMPTZ '2030-03-05 14:00:00+00', 'Licencia')""")
                .params(UUID.randomUUID(), consultorio).update();
    }

    private void evento(UUID consultorio, UUID turno) {
        jdbc.sql("""
                INSERT INTO evento_auditoria (id, consultorio_id, actor_tipo, entidad, entidad_id, accion, valor_nuevo, ip)
                VALUES (?, ?, 'SISTEMA', 'turno', ?, 'TURNO_CREADO', '{"estado":"RESERVADO"}'::jsonb, '203.0.113.10'::inet)""")
                .params(UUID.randomUUID(), consultorio, turno).update();
    }
}
