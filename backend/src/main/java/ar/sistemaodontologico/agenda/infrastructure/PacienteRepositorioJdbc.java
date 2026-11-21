package ar.sistemaodontologico.agenda.infrastructure;

import ar.sistemaodontologico.agenda.application.PacienteRepositorio;
import ar.sistemaodontologico.agenda.domain.Consentimiento;
import ar.sistemaodontologico.agenda.domain.DatosInformados;
import ar.sistemaodontologico.agenda.domain.Dni;
import ar.sistemaodontologico.agenda.domain.OrigenPaciente;
import ar.sistemaodontologico.agenda.domain.Paciente;
import ar.sistemaodontologico.agenda.domain.PacienteId;
import ar.sistemaodontologico.agenda.domain.TurnoId;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Pacientes con JdbcClient (design D2 y D6). El alta es idempotente por {@code uk_paciente_dni}; la marca de
 * revisión escribe solo {@code requiere_revision} y las columnas de lo informado (DD-38), nunca la ficha.
 */
@Repository
class PacienteRepositorioJdbc implements PacienteRepositorio {

    private static final String COLUMNAS = """
            id, nombre, apellido, dni, telefono_e164, email::text AS email, origen, requiere_revision,
            consentimiento_at, consentimiento_version, nombre_informado, apellido_informado,
            email_informado::text AS email_informado, informado_at, informado_turno_id""";

    private final JdbcClient jdbc;

    PacienteRepositorioJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean insertarSiNoExiste(ConsultorioId consultorio, Paciente paciente) {
        Consentimiento consentimiento = paciente.consentimiento();
        return jdbc.sql("""
                INSERT INTO paciente (id, consultorio_id, nombre, apellido, dni, telefono_e164, email, origen,
                                      consentimiento_at, consentimiento_version)
                VALUES (:id, :consultorio, :nombre, :apellido, :dni, :telefono, :email, :origen, :consentimientoAt,
                        :consentimientoVersion)
                ON CONFLICT (consultorio_id, dni) DO NOTHING""")
                .param("id", paciente.id().valor())
                .param("consultorio", consultorio.valor())
                .param("nombre", paciente.nombre())
                .param("apellido", paciente.apellido())
                .param("dni", paciente.dni().valor())
                .param("telefono", paciente.telefonoE164())
                .param("email", paciente.email())
                .param("origen", paciente.origen().name())
                .param("consentimientoAt", consentimiento == null ? null : Timestamp.from(consentimiento.aceptadoAt()))
                .param("consentimientoVersion", consentimiento == null ? null : consentimiento.version())
                .update() == 1;
    }

    @Override
    public Optional<Paciente> buscar(ConsultorioId consultorio, PacienteId id) {
        return jdbc.sql("SELECT " + COLUMNAS + " FROM paciente WHERE consultorio_id = :consultorio AND id = :id")
                .param("consultorio", consultorio.valor())
                .param("id", id.valor())
                .query((fila, numero) -> paciente(fila))
                .optional();
    }

    @Override
    public Optional<Paciente> buscarPorDniParaActualizar(ConsultorioId consultorio, Dni dni) {
        return jdbc.sql("SELECT " + COLUMNAS
                        + " FROM paciente WHERE consultorio_id = :consultorio AND dni = :dni FOR UPDATE")
                .param("consultorio", consultorio.valor())
                .param("dni", dni.valor())
                .query((fila, numero) -> paciente(fila))
                .optional();
    }

    @Override
    public void guardarRevision(ConsultorioId consultorio, Paciente paciente) {
        DatosInformados informados = paciente.informados();
        jdbc.sql("""
                UPDATE paciente
                   SET requiere_revision = :revision, nombre_informado = :nombre, apellido_informado = :apellido,
                       email_informado = :email, informado_at = :at, informado_turno_id = :turno, updated_at = now()
                 WHERE consultorio_id = :consultorio AND id = :id""")
                .param("revision", paciente.requiereRevision())
                .param("nombre", informados == null ? null : informados.nombre())
                .param("apellido", informados == null ? null : informados.apellido())
                .param("email", informados == null ? null : informados.email())
                .param("at", informados == null ? null : Timestamp.from(informados.informadoAt()))
                .param("turno", informados == null || informados.turnoOrigen() == null
                        ? null : informados.turnoOrigen().valor())
                .param("consultorio", consultorio.valor())
                .param("id", paciente.id().valor())
                .update();
    }

    @Override
    public void vincularTurnoDeOrigen(ConsultorioId consultorio, PacienteId paciente, TurnoId turno) {
        jdbc.sql("""
                UPDATE paciente SET informado_turno_id = :turno, updated_at = now()
                 WHERE consultorio_id = :consultorio AND id = :id""")
                .param("turno", turno.valor())
                .param("consultorio", consultorio.valor())
                .param("id", paciente.valor())
                .update();
    }

    @Override
    public void actualizarConsentimiento(ConsultorioId consultorio, PacienteId paciente, Consentimiento consentimiento) {
        jdbc.sql("""
                UPDATE paciente SET consentimiento_at = :at, consentimiento_version = :version, updated_at = now()
                 WHERE consultorio_id = :consultorio AND id = :id""")
                .param("at", Timestamp.from(consentimiento.aceptadoAt()))
                .param("version", consentimiento.version())
                .param("consultorio", consultorio.valor())
                .param("id", paciente.valor())
                .update();
    }

    private static Paciente paciente(ResultSet fila) throws SQLException {
        Timestamp consentimientoAt = fila.getTimestamp("consentimiento_at");
        Timestamp informadoAt = fila.getTimestamp("informado_at");
        UUID turnoOrigen = fila.getObject("informado_turno_id", UUID.class);
        return new Paciente(
                new PacienteId(fila.getObject("id", UUID.class)),
                fila.getString("nombre"),
                fila.getString("apellido"),
                new Dni(fila.getString("dni")),
                fila.getString("telefono_e164"),
                fila.getString("email"),
                OrigenPaciente.valueOf(fila.getString("origen")),
                fila.getBoolean("requiere_revision"),
                consentimientoAt == null ? null
                        : new Consentimiento(consentimientoAt.toInstant(), fila.getString("consentimiento_version")),
                informadoAt == null ? null
                        : new DatosInformados(fila.getString("nombre_informado"), fila.getString("apellido_informado"),
                                fila.getString("email_informado"), informadoAt.toInstant(),
                                turnoOrigen == null ? null : new TurnoId(turnoOrigen)));
    }
}
