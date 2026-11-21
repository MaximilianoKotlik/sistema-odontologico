package ar.sistemaodontologico.auditoria.infrastructure;

import ar.sistemaodontologico.auditoria.application.LecturaDeAuditoria;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    private static final TypeReference<Map<String, Object>> MAPA = new TypeReference<>() {
    };

    private static final String CONSULTA = """
            SELECT e.id, e.ocurrido_at, e.actor_tipo, e.actor_id, e.entidad, e.entidad_id, e.accion,
                   e.valor_anterior::text AS anterior, e.valor_nuevo::text AS nuevo, host(e.ip) AS ip,
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
             WHERE e.consultorio_id = :consultorio
            """;

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    LecturaDeAuditoriaJdbc(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public List<EventoLeido> listarDeTurno(ConsultorioId consultorio, UUID turnoId) {
        return jdbc.sql(CONSULTA + " AND e.entidad = 'turno' AND e.entidad_id = :turno ORDER BY e.ocurrido_at, e.id")
                .param("consultorio", consultorio.valor())
                .param("turno", turnoId)
                .query((fila, numero) -> evento(fila))
                .list();
    }

    @Override
    public List<EventoLeido> listarRegistro(ConsultorioId consultorio, CriteriosDeRegistro criterios) {
        StringBuilder consulta = new StringBuilder(CONSULTA);
        Map<String, Object> parametros = new HashMap<>();
        parametros.put("consultorio", consultorio.valor());
        parametros.put("limite", criterios.limite());
        if (criterios.turnoId() != null) {
            consulta.append(" AND e.entidad = 'turno' AND e.entidad_id = :turno");
            parametros.put("turno", criterios.turnoId());
        }
        if (criterios.inicio() != null) {
            consulta.append(" AND e.ocurrido_at >= :inicio");
            parametros.put("inicio", Timestamp.from(criterios.inicio()));
        }
        if (criterios.finExclusivo() != null) {
            consulta.append(" AND e.ocurrido_at < :fin");
            parametros.put("fin", Timestamp.from(criterios.finExclusivo()));
        }
        if (!criterios.acciones().isEmpty()) {
            consulta.append(" AND e.accion IN (:acciones)");
            parametros.put("acciones", criterios.acciones());
        }
        if (criterios.antesDe() != null) {
            consulta.append(" AND (e.ocurrido_at, e.id) < (CAST(:antesDe AS timestamptz), CAST(:antesDeId AS uuid))");
            parametros.put("antesDe", Timestamp.from(criterios.antesDe()));
            parametros.put("antesDeId", criterios.antesDeId());
        }
        consulta.append(" ORDER BY e.ocurrido_at DESC, e.id DESC LIMIT :limite");
        return jdbc.sql(consulta.toString()).params(parametros).query((fila, numero) -> evento(fila)).list();
    }

    @Override
    public boolean existeTurno(ConsultorioId consultorio, UUID turnoId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM turno WHERE consultorio_id = :consultorio AND id = :turno)")
                .param("consultorio", consultorio.valor())
                .param("turno", turnoId)
                .query(Boolean.class)
                .single();
    }

    @Override
    public ZoneId zonaHoraria(ConsultorioId consultorio) {
        return ZoneId.of(jdbc.sql("SELECT zona_horaria FROM consultorio WHERE id = :consultorio")
                .param("consultorio", consultorio.valor())
                .query(String.class)
                .single());
    }

    private EventoLeido evento(ResultSet fila) throws SQLException {
        return new EventoLeido(fila.getObject("id", UUID.class), fila.getTimestamp("ocurrido_at").toInstant(),
                actor(fila), fila.getString("accion"), fila.getString("entidad"),
                fila.getObject("entidad_id", UUID.class), mapa(fila.getString("anterior")),
                mapa(fila.getString("nuevo")), fila.getString("ip"), contexto(fila));
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

    private Map<String, Object> mapa(String valor) {
        if (valor == null) {
            return null;
        }
        try {
            return json.readValue(valor, MAPA);
        } catch (JsonProcessingException e) {
            // Sin la causa ni el valor: puede contener datos personales.
            throw new IllegalStateException("No se pudieron leer los valores de un evento de auditoría");
        }
    }
}
