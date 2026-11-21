package ar.sistemaodontologico.agenda.infrastructure;

import ar.sistemaodontologico.agenda.application.TurnoGuardado;
import ar.sistemaodontologico.agenda.application.TurnoRepositorio;
import ar.sistemaodontologico.agenda.domain.CanceladoPor;
import ar.sistemaodontologico.agenda.domain.Duracion;
import ar.sistemaodontologico.agenda.domain.EstadoTurno;
import ar.sistemaodontologico.agenda.domain.Intervalo;
import ar.sistemaodontologico.agenda.domain.OrigenTurno;
import ar.sistemaodontologico.agenda.domain.PacienteId;
import ar.sistemaodontologico.agenda.domain.Turno;
import ar.sistemaodontologico.agenda.domain.TurnoId;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Turnos con JdbcClient (design D2). {@code fin} se escribe siempre derivado del dominio, y el {@code UPDATE} lleva la
 * versión esperada: si no coincide, no actualiza ninguna fila (RN-GL-02).
 */
@Repository
class TurnoRepositorioJdbc implements TurnoRepositorio {

    private static final String COLUMNAS = """
            id, consultorio_id, paciente_id, tipo_prestacion_id, inicio, duracion_min, estado, es_sobreturno, origen,
            cancelado_por, motivo_cancelacion, nota_interna, version""";

    private final JdbcClient jdbc;

    TurnoRepositorioJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<TurnoGuardado> buscar(ConsultorioId consultorio, TurnoId id) {
        return jdbc.sql("SELECT " + COLUMNAS + " FROM turno WHERE consultorio_id = :consultorio AND id = :id")
                .param("consultorio", consultorio.valor())
                .param("id", id.valor())
                .query((fila, numero) -> new TurnoGuardado(turno(fila), fila.getLong("version")))
                .optional();
    }

    @Override
    public List<Turno> listarActivosQueIntersectan(ConsultorioId consultorio, Intervalo intervalo) {
        return jdbc.sql("SELECT " + COLUMNAS + """
                         FROM turno
                        WHERE consultorio_id = :consultorio
                          AND estado IN ('RESERVADO', 'CONFIRMADO')
                          AND tstzrange(inicio, fin, '[)') && tstzrange(:inicio, :fin, '[)')
                        ORDER BY inicio""")
                .param("consultorio", consultorio.valor())
                .param("inicio", Timestamp.from(intervalo.inicio()))
                .param("fin", Timestamp.from(intervalo.fin()))
                .query((fila, numero) -> turno(fila))
                .list();
    }

    @Override
    public void insertar(Turno turno) {
        jdbc.sql("""
                INSERT INTO turno (id, consultorio_id, paciente_id, tipo_prestacion_id, inicio, fin, duracion_min,
                                   estado, es_sobreturno, origen, cancelado_por, motivo_cancelacion, nota_interna)
                VALUES (:id, :consultorio, :paciente, :prestacion, :inicio, :fin, :duracion, :estado, :sobreturno,
                        :origen, :canceladoPor, :motivo, :nota)""")
                .param("id", turno.id().valor())
                .param("consultorio", turno.consultorioId().valor())
                .param("paciente", turno.pacienteId().valor())
                .param("prestacion", turno.tipoPrestacionId())
                .param("inicio", Timestamp.from(turno.inicio()))
                .param("fin", Timestamp.from(turno.fin()))
                .param("duracion", turno.duracion().minutos())
                .param("estado", turno.estado().name())
                .param("sobreturno", turno.esSobreturno())
                .param("origen", turno.origen().name())
                .param("canceladoPor", nombre(turno.canceladoPor()))
                .param("motivo", turno.motivoCancelacion())
                .param("nota", turno.notaInterna())
                .update();
    }

    @Override
    public long actualizar(Turno turno, long versionEsperada) {
        int filas = jdbc.sql("""
                UPDATE turno
                   SET inicio = :inicio, fin = :fin, duracion_min = :duracion, estado = :estado,
                       cancelado_por = :canceladoPor, motivo_cancelacion = :motivo, nota_interna = :nota,
                       version = version + 1, updated_at = now()
                 WHERE consultorio_id = :consultorio AND id = :id AND version = :version""")
                .param("inicio", Timestamp.from(turno.inicio()))
                .param("fin", Timestamp.from(turno.fin()))
                .param("duracion", turno.duracion().minutos())
                .param("estado", turno.estado().name())
                .param("canceladoPor", nombre(turno.canceladoPor()))
                .param("motivo", turno.motivoCancelacion())
                .param("nota", turno.notaInterna())
                .param("consultorio", turno.consultorioId().valor())
                .param("id", turno.id().valor())
                .param("version", versionEsperada)
                .update();
        if (filas == 0) {
            throw new OptimisticLockingFailureException("El turno cambió desde que se leyó");
        }
        return versionEsperada + 1;
    }

    @Override
    public void limpiarNovedad(ConsultorioId consultorio, TurnoId id, Instant novedadVista) {
        jdbc.sql("""
                UPDATE turno SET novedad_paciente_at = NULL
                 WHERE consultorio_id = :consultorio AND id = :id
                   AND date_trunc('milliseconds', novedad_paciente_at) <= :novedad""")
                .param("consultorio", consultorio.valor())
                .param("id", id.valor())
                .param("novedad", Timestamp.from(novedadVista))
                .update();
    }

    @Override
    public long contarFuturosActivosPorLink(ConsultorioId consultorio, PacienteId paciente, Instant ahora) {
        return jdbc.sql("""
                SELECT count(*) FROM turno
                 WHERE consultorio_id = :consultorio AND paciente_id = :paciente AND origen = 'LINK'
                   AND estado IN ('RESERVADO', 'CONFIRMADO') AND inicio > :ahora""")
                .param("consultorio", consultorio.valor())
                .param("paciente", paciente.valor())
                .param("ahora", Timestamp.from(ahora))
                .query(Long.class)
                .single();
    }

    @Override
    public void marcarNovedadPaciente(ConsultorioId consultorio, TurnoId turno, Instant ahora) {
        jdbc.sql("""
                UPDATE turno SET novedad_paciente_at = :ahora, updated_at = now()
                 WHERE consultorio_id = :consultorio AND id = :id""")
                .param("ahora", Timestamp.from(ahora))
                .param("consultorio", consultorio.valor())
                .param("id", turno.valor())
                .update();
    }

    private static Turno turno(ResultSet fila) throws SQLException {
        String canceladoPor = fila.getString("cancelado_por");
        return Turno.restaurar(
                new TurnoId(fila.getObject("id", UUID.class)),
                new ConsultorioId(fila.getObject("consultorio_id", UUID.class)),
                new PacienteId(fila.getObject("paciente_id", UUID.class)),
                fila.getObject("tipo_prestacion_id", UUID.class),
                instante(fila, "inicio"),
                new Duracion(fila.getInt("duracion_min")),
                EstadoTurno.valueOf(fila.getString("estado")),
                fila.getBoolean("es_sobreturno"),
                OrigenTurno.valueOf(fila.getString("origen")),
                canceladoPor == null ? null : CanceladoPor.valueOf(canceladoPor),
                fila.getString("motivo_cancelacion"),
                fila.getString("nota_interna"));
    }

    static Instant instante(ResultSet fila, String columna) throws SQLException {
        return fila.getTimestamp(columna).toInstant();
    }

    private static String nombre(Enum<?> valor) {
        return valor == null ? null : valor.name();
    }
}
