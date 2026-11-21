package ar.sistemaodontologico.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.sistemaodontologico.compartido.IntegracionPostgresTest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Migración de paciente, turno y bloqueo (C-10, tareas 2.1 a 2.4; design D8). Inserta con SQL directo para probar
 * que la base protege las invariantes aunque se saltee la aplicación (design D1). Datos ficticios (regla dura 17).
 */
class MigracionAgendaTest extends IntegracionPostgresTest {

    /** Lunes 2026-11-23 a las 10:00 en Buenos Aires (13:00 UTC). */
    private static final String DIEZ = "2026-11-23T13:00:00Z";

    @Autowired
    private JdbcTemplate jdbc;

    private UUID consultorio;
    private UUID prestacion;
    private UUID paciente;

    @BeforeEach
    void crearConsultorio() {
        consultorio = insertarConsultorio();
        prestacion = insertarPrestacion(consultorio);
        paciente = insertarPaciente(consultorio, dniUnico(), "+5491100000001");
    }

    // --- paciente (2.1) ---

    @Test
    void elDniEsUnicoPorConsultorio() {
        String dni = dniUnico();
        insertarPaciente(consultorio, dni, "+5491100000002");

        assertThatThrownBy(() -> insertarPaciente(consultorio, dni, "+5491100000003"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_paciente_dni");
        assertThatCode(() -> insertarPaciente(insertarConsultorio(), dni, "+5491100000003")).doesNotThrowAnyException();
    }

    @Test
    void elDniTiene7u8Digitos() {
        for (String invalido : new String[] {"90A00001", "123456", "9.000.01"}) {
            assertThatThrownBy(() -> insertarPaciente(consultorio, invalido, "+5491100000004"))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("ck_paciente_dni");
        }
        // Más de 8 lo frena el largo de la columna antes que el CHECK.
        assertThatThrownBy(() -> insertarPaciente(consultorio, "123456789", "+5491100000004"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> insertarPaciente(consultorio, "9" + sufijoNumerico(6), "+5491100000004"))
                .doesNotThrowAnyException();
    }

    @Test
    void elTelefonoSePuedeCompartir() {
        assertThatCode(() -> insertarPaciente(consultorio, dniUnico(), "+5491100000001")).doesNotThrowAnyException();
    }

    @Test
    void unPacientePorLinkNecesitaConsentimiento() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO paciente (id, consultorio_id, nombre, apellido, dni, telefono_e164, origen)
                VALUES (?, ?, 'Ana', 'Prueba', ?, '+5491100000005', 'LINK')""", UUID.randomUUID(), consultorio, dniUnico()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_paciente_consentimiento");
        assertThatCode(() -> jdbc.update("""
                INSERT INTO paciente (id, consultorio_id, nombre, apellido, dni, telefono_e164, origen, consentimiento_at,
                                      consentimiento_version)
                VALUES (?, ?, 'Ana', 'Prueba', ?, '+5491100000005', 'LINK', now(), '2026-11-borrador')""",
                UUID.randomUUID(), consultorio, dniUnico())).doesNotThrowAnyException();
    }

    @Test
    void elEmailNoDistingueMayusculas() {
        UUID id = insertarPaciente(consultorio, dniUnico(), "+5491100000006");
        jdbc.update("UPDATE paciente SET email = 'Ana.Prueba@Example.com' WHERE id = ?", id);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM paciente WHERE id = ? AND email = 'ana.prueba@example.com'",
                Integer.class, id)).isEqualTo(1);
    }

    @Test
    void elPacienteArrancaSinRevisionPendiente() {
        assertThat(jdbc.queryForObject("SELECT requiere_revision FROM paciente WHERE id = ?", Boolean.class, paciente))
                .isFalse();
    }

    // --- datos informados en la reserva (DD-38) ---

    @Test
    void sinRevisionPendienteNoHayDatosInformados() {
        UUID turno = insertarTurno(consultorio, DIEZ, 30, "RESERVADO", false);
        String[] asignaciones = {"nombre_informado = 'Anna'", "apellido_informado = 'Ejemplo'",
            "email_informado = 'anna@example.com'", "informado_at = now()", "informado_turno_id = '" + turno + "'"};

        for (String asignacion : asignaciones) {
            assertThatThrownBy(() -> jdbc.update("UPDATE paciente SET " + asignacion + " WHERE id = ?", paciente))
                    .as(asignacion)
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("ck_paciente_informado");
        }
    }

    @Test
    void conRevisionPendienteSeGuardaLoInformadoYAlRevisarSeBorra() {
        UUID turno = insertarTurno(consultorio, DIEZ, 30, "RESERVADO", false);

        assertThat(jdbc.update("""
                UPDATE paciente SET requiere_revision = true, nombre_informado = 'Anna', apellido_informado = 'Ejemplo',
                       email_informado = 'Anna@Example.com', informado_at = now(), informado_turno_id = ?
                WHERE id = ?""", turno, paciente)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM paciente WHERE id = ? AND email_informado = 'anna@example.com'", Integer.class,
                paciente)).isEqualTo(1);

        // Revisar sin limpiar lo informado no pasa; revisar y limpiar, sí (C-17).
        assertThatThrownBy(() -> jdbc.update("UPDATE paciente SET requiere_revision = false WHERE id = ?", paciente))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_paciente_informado");
        assertThat(jdbc.update("""
                UPDATE paciente SET requiere_revision = false, nombre_informado = NULL, apellido_informado = NULL,
                       email_informado = NULL, informado_at = NULL, informado_turno_id = NULL
                WHERE id = ?""", paciente)).isEqualTo(1);
    }

    @Test
    void elTurnoInformadoEsDelMismoConsultorio() {
        UUID otro = insertarConsultorio();
        insertarPrestacion(otro);
        UUID turnoDeOtro = insertarTurnoEn(otro, DIEZ);

        assertThatThrownBy(() -> jdbc.update("""
                UPDATE paciente SET requiere_revision = true, nombre_informado = 'Anna', apellido_informado = 'Ejemplo',
                       informado_at = now(), informado_turno_id = ?
                WHERE id = ?""", turnoDeOtro, paciente))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_paciente_informado_turno");
    }

    @Test
    void elRolDeAplicacionNoBorraPacientesNiTurnos() {
        UUID turno = insertarTurno(consultorio, DIEZ, 30, "RESERVADO", false);

        assertThatThrownBy(() -> jdbc.update("DELETE FROM turno WHERE id = ?", turno))
                .rootCause().hasMessageContaining("permission denied for table turno");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM paciente WHERE id = ?", paciente))
                .rootCause().hasMessageContaining("permission denied for table paciente");
    }

    // --- turno (2.2) ---

    @Test
    void laDuracionVaDe5A480EnMultiplosDe5() {
        for (int invalida : new int[] {0, 42, 485}) {
            assertThatThrownBy(() -> insertarTurno(consultorio, DIEZ, invalida, "RESERVADO", false))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("ck_turno_duracion");
        }
    }

    @Test
    void laBaseRechazaUnFinIncoherente() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO turno (id, consultorio_id, paciente_id, tipo_prestacion_id, inicio, fin, duracion_min, estado,
                                   origen)
                VALUES (?, ?, ?, ?, CAST(? AS timestamptz), CAST(? AS timestamptz) + interval '40 minutes', 30,
                        'RESERVADO', 'MANUAL')""", UUID.randomUUID(), consultorio, paciente, prestacion, DIEZ, DIEZ))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_turno_fin");
    }

    @Test
    void elInicioCaeEnLaGrillaDe5Minutos() {
        for (String fueraDeGrilla : new String[] {"2026-11-23T13:03:00Z", "2026-11-23T13:00:30Z",
                "2026-11-23T13:00:00.6Z"}) {
            assertThatThrownBy(() -> insertarTurno(consultorio, fueraDeGrilla, 30, "RESERVADO", false))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("ck_turno_grilla");
        }
    }

    @Test
    void estadoYOrigenValidos() {
        assertThatThrownBy(() -> insertarTurno(consultorio, DIEZ, 30, "PENDIENTE", false))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_turno_estado");
        assertThatThrownBy(() -> insertarTurnoConOrigen("TELEFONO", false))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_turno_origen");
    }

    @Test
    void unSobreturnoSiempreEsManual() {
        assertThatThrownBy(() -> insertarTurnoConOrigen("LINK", true))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_turno_sobreturno_manual");
        assertThatCode(() -> insertarTurnoConOrigen("MANUAL", true)).doesNotThrowAnyException();
    }

    @Test
    void laBaseExigeCoherenciaDeLaCancelacion() {
        assertThatThrownBy(() -> insertarTurnoCancelado("CONFIRMADO", "ODONTOLOGO", null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_turno_cancelacion");
        assertThatThrownBy(() -> insertarTurnoCancelado("CANCELADO", null, null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_turno_cancelacion");
        assertThatThrownBy(() -> insertarTurnoCancelado("CANCELADO", "SISTEMA", null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_turno_cancelacion");
        assertThatThrownBy(() -> insertarTurnoCancelado("RESERVADO", null, "Sin motivo"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_turno_cancelacion");
        assertThatCode(() -> insertarTurnoCancelado("CANCELADO", "PACIENTE", "Viaje")).doesNotThrowAnyException();
    }

    @Test
    void unTurnoNoReferenciaDatosDeOtroConsultorio() {
        UUID otro = insertarConsultorio();
        UUID prestacionDeOtro = insertarPrestacion(otro);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO turno (id, consultorio_id, paciente_id, tipo_prestacion_id, inicio, fin, duracion_min, estado,
                                   origen)
                VALUES (?, ?, ?, ?, CAST(? AS timestamptz), CAST(? AS timestamptz) + interval '30 minutes', 30,
                        'RESERVADO', 'MANUAL')""", UUID.randomUUID(), otro, paciente, prestacionDeOtro, DIEZ, DIEZ))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_turno_paciente");
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO turno (id, consultorio_id, paciente_id, tipo_prestacion_id, inicio, fin, duracion_min, estado,
                                   origen)
                VALUES (?, ?, ?, ?, CAST(? AS timestamptz), CAST(? AS timestamptz) + interval '30 minutes', 30,
                        'RESERVADO', 'MANUAL')""", UUID.randomUUID(), consultorio, paciente, prestacionDeOtro, DIEZ, DIEZ))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_turno_tipo_prestacion");
    }

    // --- ex_turno_sin_solapamiento (2.3) ---

    @Test
    void laBaseRechazaElSolapamientoDeTurnosActivos() {
        insertarTurno(consultorio, DIEZ, 30, "RESERVADO", false);

        assertThatThrownBy(() -> insertarTurno(consultorio, "2026-11-23T13:15:00Z", 30, "CONFIRMADO", false))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ex_turno_sin_solapamiento");
    }

    @Test
    void contiguosSobreturnosCanceladosAusentesYOtroConsultorioPasan() {
        insertarTurno(consultorio, DIEZ, 30, "RESERVADO", false);

        assertThatCode(() -> insertarTurno(consultorio, "2026-11-23T13:30:00Z", 30, "RESERVADO", false))
                .doesNotThrowAnyException();
        assertThatCode(() -> insertarTurno(consultorio, DIEZ, 20, "RESERVADO", true)).doesNotThrowAnyException();
        assertThatCode(() -> insertarTurnoCancelado("CANCELADO", "ODONTOLOGO", null)).doesNotThrowAnyException();
        assertThatCode(() -> insertarTurno(consultorio, DIEZ, 30, "AUSENTE", false)).doesNotThrowAnyException();
        UUID otro = insertarConsultorio();
        insertarPrestacion(otro);
        assertThatCode(() -> insertarTurnoEn(otro, DIEZ)).doesNotThrowAnyException();
    }

    @Test
    void reactivarUnHorarioOcupadoFalla() {
        UUID cancelado = insertarTurnoCancelado("CANCELADO", "PACIENTE", null);
        insertarTurno(consultorio, DIEZ, 30, "RESERVADO", false);

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE turno SET estado = 'RESERVADO', cancelado_por = NULL WHERE id = ?", cancelado))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ex_turno_sin_solapamiento");
    }

    @Test
    void elFinYLaGrillaNoDependenDeLaZonaDeLaSesion() throws Exception {
        // ck_turno_fin y ck_turno_grilla comparan epochs: el resultado es el mismo en cualquier TimeZone de sesión.
        for (String zona : new String[] {"UTC", "America/Argentina/Buenos_Aires", "Asia/Kolkata"}) {
            try (Connection owner = DriverManager.getConnection(POSTGRES.getJdbcUrl(), OWNER_USER, OWNER_PASSWORD)) {
                owner.setAutoCommit(false);
                try (Statement sesion = owner.createStatement()) {
                    sesion.execute("SET TIME ZONE '" + zona + "'");
                }
                try (PreparedStatement insercion = owner.prepareStatement("""
                        INSERT INTO turno (id, consultorio_id, paciente_id, tipo_prestacion_id, inicio, fin, duracion_min,
                                           estado, origen)
                        VALUES (?, ?, ?, ?, CAST(? AS timestamptz), CAST(? AS timestamptz) + interval '45 minutes', 45,
                                'RESERVADO', 'MANUAL')""")) {
                    insercion.setObject(1, UUID.randomUUID());
                    insercion.setObject(2, consultorio);
                    insercion.setObject(3, paciente);
                    insercion.setObject(4, prestacion);
                    insercion.setString(5, "2026-11-24T13:00:00Z");
                    insercion.setString(6, "2026-11-24T13:00:00Z");
                    assertThat(insercion.executeUpdate()).as("zona %s", zona).isEqualTo(1);
                } finally {
                    owner.rollback();
                }
            }
        }
    }

    @Test
    void losIndicesEmpiezanPorElConsultorio() {
        assertThat(jdbc.queryForList("""
                SELECT indexdef FROM pg_indexes
                WHERE tablename IN ('paciente', 'turno', 'bloqueo') AND indexname LIKE 'ix_%'""", String.class))
                .hasSize(4)
                .allSatisfy(definicion -> assertThat(definicion).contains("(consultorio_id"));
    }

    // --- bloqueo (2.4) ---

    @Test
    void elFinDelBloqueoEsPosteriorAlInicio() {
        assertThatThrownBy(() -> insertarBloqueo(consultorio, "2026-11-23T21:00:00Z", "2026-11-23T17:00:00Z"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_bloqueo_rango");
    }

    @Test
    void elBloqueoCaeEnLaGrillaDe5Minutos() {
        assertThatThrownBy(() -> insertarBloqueo(consultorio, "2026-11-23T17:02:00Z", "2026-11-23T21:00:00Z"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_bloqueo_grilla");
        assertThatThrownBy(() -> insertarBloqueo(consultorio, "2026-11-23T17:00:00Z", "2026-11-23T21:00:00.6Z"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_bloqueo_grilla");
    }

    @Test
    void dosBloqueosSuperpuestosPasanYSePuedenBorrar() {
        UUID todoElDia = insertarBloqueo(consultorio, "2026-11-23T12:00:00Z", "2026-11-23T21:00:00Z");
        UUID adentro = insertarBloqueo(consultorio, "2026-11-23T13:00:00Z", "2026-11-23T14:00:00Z");

        assertThat(jdbc.update("DELETE FROM bloqueo WHERE id IN (?, ?)", todoElDia, adentro)).isEqualTo(2);
    }

    @Test
    void elBloqueoTieneIndiceGistPorRango() {
        assertThat(jdbc.queryForObject("SELECT indexdef FROM pg_indexes WHERE indexname = 'ix_bloqueo_rango'",
                String.class)).contains("gist").contains("consultorio_id").contains("tstzrange");
    }

    private UUID insertarConsultorio() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO consultorio (id, nombre, slug, plan) VALUES (?, 'Consultorio Demo', ?, 'PRUEBA')",
                id, "agenda-" + id.toString().substring(0, 8));
        return id;
    }

    private UUID insertarPrestacion(UUID enConsultorio) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tipo_prestacion (id, consultorio_id, nombre, duracion_min, orden)
                VALUES (?, ?, 'Consulta', 30, 1)""", id, enConsultorio);
        return id;
    }

    private UUID insertarPaciente(UUID enConsultorio, String dni, String telefono) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO paciente (id, consultorio_id, nombre, apellido, dni, telefono_e164, origen)
                VALUES (?, ?, 'Paciente', 'Prueba', ?, ?, 'MANUAL')""", id, enConsultorio, dni, telefono);
        return id;
    }

    private UUID insertarTurno(UUID enConsultorio, String inicio, int duracion, String estado, boolean sobreturno) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO turno (id, consultorio_id, paciente_id, tipo_prestacion_id, inicio, fin, duracion_min, estado,
                                   es_sobreturno, origen)
                VALUES (?, ?, ?, ?, CAST(? AS timestamptz), CAST(? AS timestamptz) + make_interval(mins => ?), ?, ?, ?,
                        'MANUAL')""", id, enConsultorio, paciente, prestacion, inicio, inicio, duracion, duracion, estado,
                sobreturno);
        return id;
    }

    private UUID insertarTurnoEn(UUID otroConsultorio, String inicio) {
        UUID pacienteDeOtro = insertarPaciente(otroConsultorio, dniUnico(), "+5491100000007");
        UUID prestacionDeOtro = jdbc.queryForObject("SELECT id FROM tipo_prestacion WHERE consultorio_id = ?", UUID.class,
                otroConsultorio);
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO turno (id, consultorio_id, paciente_id, tipo_prestacion_id, inicio, fin, duracion_min, estado,
                                   origen)
                VALUES (?, ?, ?, ?, CAST(? AS timestamptz), CAST(? AS timestamptz) + interval '30 minutes', 30,
                        'RESERVADO', 'MANUAL')""", id, otroConsultorio, pacienteDeOtro, prestacionDeOtro, inicio, inicio);
        return id;
    }

    private UUID insertarTurnoConOrigen(String origen, boolean sobreturno) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO turno (id, consultorio_id, paciente_id, tipo_prestacion_id, inicio, fin, duracion_min, estado,
                                   es_sobreturno, origen)
                VALUES (?, ?, ?, ?, CAST(? AS timestamptz), CAST(? AS timestamptz) + interval '30 minutes', 30,
                        'RESERVADO', ?, ?)""", id, consultorio, paciente, prestacion, DIEZ, DIEZ, sobreturno, origen);
        return id;
    }

    private UUID insertarTurnoCancelado(String estado, String canceladoPor, String motivo) {
        UUID id = UUID.randomUUID();
        // Ausente o cancelado para no chocar con los turnos activos de cada test.
        jdbc.update("""
                INSERT INTO turno (id, consultorio_id, paciente_id, tipo_prestacion_id, inicio, fin, duracion_min, estado,
                                   origen, cancelado_por, motivo_cancelacion)
                VALUES (?, ?, ?, ?, CAST(? AS timestamptz), CAST(? AS timestamptz) + interval '30 minutes', 30, ?,
                        'MANUAL', ?, ?)""", id, consultorio, paciente, prestacion, DIEZ, DIEZ, estado, canceladoPor, motivo);
        return id;
    }

    private UUID insertarBloqueo(UUID enConsultorio, String inicio, String fin) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO bloqueo (id, consultorio_id, inicio, fin, motivo)
                VALUES (?, ?, CAST(? AS timestamptz), CAST(? AS timestamptz), 'Curso')""", id, enConsultorio, inicio, fin);
        return id;
    }

    private static String dniUnico() {
        return "9" + sufijoNumerico(7);
    }

    private static String sufijoNumerico(int digitos) {
        long valor = Math.floorMod(UUID.randomUUID().getMostSignificantBits(), (long) Math.pow(10, digitos));
        return String.format("%0" + digitos + "d", valor);
    }
}
