package ar.sistemaodontologico.agenda.infrastructure;

import ar.sistemaodontologico.agenda.application.DetalleDeTurno;
import ar.sistemaodontologico.agenda.application.TurnoDeAgenda;
import ar.sistemaodontologico.agenda.application.VistaDeAgendaRepositorio;
import ar.sistemaodontologico.agenda.domain.CanceladoPor;
import ar.sistemaodontologico.agenda.domain.EstadoTurno;
import ar.sistemaodontologico.agenda.domain.Intervalo;
import ar.sistemaodontologico.agenda.domain.OrigenTurno;
import ar.sistemaodontologico.agenda.domain.TurnoId;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Lecturas de la agenda con JdbcClient (C-12, design D1 y D2). Solo trae lo que muestra la agenda: el nombre del
 * paciente y si hay nota, nunca su DNI, teléfono, email ni el texto de la nota. Los joins van por la clave compuesta
 * con {@code consultorio_id} (C-02).
 */
@Repository
class VistaDeAgendaRepositorioJdbc implements VistaDeAgendaRepositorio {

    static final String SELECT_TURNO = """
            SELECT t.id, t.inicio, t.fin, t.duracion_min, t.estado, t.es_sobreturno, t.origen, t.cancelado_por,
                   t.nota_interna IS NOT NULL AS tiene_nota, t.novedad_paciente_at, t.version,
                   pr.id AS prestacion_id, pr.nombre AS prestacion_nombre,
                   pa.id AS paciente_id, pa.nombre AS paciente_nombre, pa.apellido AS paciente_apellido
              FROM turno t
              JOIN tipo_prestacion pr ON pr.consultorio_id = t.consultorio_id AND pr.id = t.tipo_prestacion_id
              JOIN paciente pa ON pa.consultorio_id = t.consultorio_id AND pa.id = t.paciente_id
            """;

    private final JdbcClient jdbc;

    VistaDeAgendaRepositorioJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<TurnoDeAgenda> listarQueIntersectan(ConsultorioId consultorio, Intervalo intervalo) {
        return jdbc.sql(SELECT_TURNO + """
                 WHERE t.consultorio_id = :consultorio
                   AND tstzrange(t.inicio, t.fin, '[)') && tstzrange(:inicio, :fin, '[)')
                 ORDER BY t.inicio, t.id""")
                .param("consultorio", consultorio.valor())
                .param("inicio", Timestamp.from(intervalo.inicio()))
                .param("fin", Timestamp.from(intervalo.fin()))
                .query((fila, numero) -> turno(fila))
                .list();
    }

    @Override
    public List<TurnoDeAgenda> listarActivosSinSobreturnoDesde(ConsultorioId consultorio, Instant desde) {
        return jdbc.sql(SELECT_TURNO + """
                 WHERE t.consultorio_id = :consultorio AND t.estado IN ('RESERVADO', 'CONFIRMADO')
                   AND NOT t.es_sobreturno AND t.inicio >= :desde
                 ORDER BY t.inicio, t.id""")
                .param("consultorio", consultorio.valor())
                .param("desde", Timestamp.from(desde))
                .query((fila, numero) -> turno(fila))
                .list();
    }

    @Override
    public Optional<DetalleDeTurno> buscarDetalle(ConsultorioId consultorio, TurnoId id) {
        return jdbc.sql(SELECT_TURNO.replace("SELECT t.id,", """
                SELECT pa.dni, pa.telefono_e164, pa.email, pa.requiere_revision, t.nota_interna,
                       t.motivo_cancelacion, t.id,""") + " WHERE t.consultorio_id = :consultorio AND t.id = :id")
                .param("consultorio", consultorio.valor())
                .param("id", id.valor())
                .query((fila, numero) -> new DetalleDeTurno(turno(fila), fila.getString("dni"),
                        fila.getString("telefono_e164"), fila.getString("email"), fila.getBoolean("requiere_revision"),
                        fila.getString("nota_interna"), fila.getString("motivo_cancelacion"), Optional.empty()))
                .optional();
    }

    static TurnoDeAgenda turno(ResultSet fila) throws SQLException {
        String canceladoPor = fila.getString("cancelado_por");
        return new TurnoDeAgenda(
                fila.getObject("id", UUID.class),
                instante(fila, "inicio"),
                instante(fila, "fin"),
                fila.getInt("duracion_min"),
                EstadoTurno.valueOf(fila.getString("estado")),
                fila.getBoolean("es_sobreturno"),
                OrigenTurno.valueOf(fila.getString("origen")),
                fila.getObject("prestacion_id", UUID.class),
                fila.getString("prestacion_nombre"),
                fila.getObject("paciente_id", UUID.class),
                fila.getString("paciente_nombre"),
                fila.getString("paciente_apellido"),
                fila.getBoolean("tiene_nota"),
                canceladoPor == null ? null : CanceladoPor.valueOf(canceladoPor),
                null,
                instante(fila, "novedad_paciente_at"),
                fila.getLong("version"));
    }

    /** Con los microsegundos que guarda Postgres: el visto compara contra este valor (design D5). */
    private static Instant instante(ResultSet fila, String columna) throws SQLException {
        Timestamp valor = fila.getTimestamp(columna);
        return valor == null ? null : valor.toInstant();
    }
}
