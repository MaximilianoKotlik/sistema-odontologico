package ar.sistemaodontologico.auditoria.infrastructure;

import ar.sistemaodontologico.auditoria.application.LecturaDeAuditoria;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Lectura con el actor y el contexto en una sola consulta (C-18, design D3). Todos los joins repiten el consultorio: un
 * {@code actor_id} mal cargado no puede traer un nombre ajeno. La marca de revisión se resuelve acá para que el nombre de
 * la ficha nunca llegue como autor de la reserva.
 */
@Repository
class LecturaDeAuditoriaJdbc implements LecturaDeAuditoria {

    private final JdbcClient jdbc;

    LecturaDeAuditoriaJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<EventoLeido> listarDeTurno(ConsultorioId consultorio, UUID turnoId) {
        return jdbc.sql("""
                SELECT e.id, e.ocurrido_at, e.actor_tipo, e.actor_id, e.accion,
                       u.nombre AS u_nombre, u.apellido AS u_apellido,
                       pa.nombre AS pa_nombre, pa.apellido AS pa_apellido,
                       t.inicio AS t_inicio, pt.nombre AS pt_nombre, pt.apellido AS pt_apellido,
                       (e.actor_tipo = 'PACIENTE' AND e.entidad = 'turno' AND pa.requiere_revision
                        AND pa.informado_turno_id = e.entidad_id) IS TRUE AS actor_en_revision
                  FROM evento_auditoria e
                  LEFT JOIN usuario u   ON e.actor_tipo = 'ODONTOLOGO' AND u.id = e.actor_id
                                       AND u.consultorio_id = e.consultorio_id
                  LEFT JOIN paciente pa ON e.actor_tipo = 'PACIENTE' AND pa.id = e.actor_id
                                       AND pa.consultorio_id = e.consultorio_id
                  LEFT JOIN turno t     ON e.entidad = 'turno' AND t.id = e.entidad_id
                                       AND t.consultorio_id = e.consultorio_id
                  LEFT JOIN paciente pt ON pt.id = t.paciente_id AND pt.consultorio_id = e.consultorio_id
                 WHERE e.consultorio_id = :consultorio AND e.entidad = 'turno' AND e.entidad_id = :turno
                 ORDER BY e.ocurrido_at, e.id""")
                .param("consultorio", consultorio.valor())
                .param("turno", turnoId)
                .query((fila, numero) -> evento(fila))
                .list();
    }

    private static EventoLeido evento(ResultSet fila) throws SQLException {
        return new EventoLeido(fila.getObject("id", UUID.class), fila.getTimestamp("ocurrido_at").toInstant(),
                actor(fila), fila.getString("accion"), contexto(fila));
    }

    private static ActorLeido actor(ResultSet fila) throws SQLException {
        String tipo = fila.getString("actor_tipo");
        boolean enRevision = fila.getBoolean("actor_en_revision");
        String nombre = enRevision ? null : nombreDelActor(fila, tipo);
        return new ActorLeido(tipo, fila.getObject("actor_id", UUID.class), nombre, enRevision);
    }

    private static String nombreDelActor(ResultSet fila, String tipo) throws SQLException {
        return switch (tipo) {
            case "ODONTOLOGO" -> nombreCompleto(fila.getString("u_nombre"), fila.getString("u_apellido"));
            case "PACIENTE" -> nombreCompleto(fila.getString("pa_nombre"), fila.getString("pa_apellido"));
            default -> null;
        };
    }

    private static ContextoDeTurno contexto(ResultSet fila) throws SQLException {
        Timestamp inicio = fila.getTimestamp("t_inicio");
        if (inicio == null) {
            return null;
        }
        return new ContextoDeTurno(inicio.toInstant(),
                nombreCompleto(fila.getString("pt_nombre"), fila.getString("pt_apellido")));
    }

    private static String nombreCompleto(String nombre, String apellido) {
        return nombre == null ? null : nombre + " " + apellido;
    }
}
