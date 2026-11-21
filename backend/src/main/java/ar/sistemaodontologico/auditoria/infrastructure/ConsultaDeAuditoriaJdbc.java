package ar.sistemaodontologico.auditoria.infrastructure;

import ar.sistemaodontologico.compartido.application.ConsultaDeAuditoria;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import ar.sistemaodontologico.compartido.domain.auditoria.ActorTipo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Lecturas de {@code evento_auditoria} para otros módulos (C-12, design D3), con el índice
 * {@code ix_evento_auditoria_consultorio_entidad} de C-03. El último por entidad sale en una sola consulta con
 * {@code DISTINCT ON}; a igual instante desempata el id, que es UUIDv7 y crece con el tiempo.
 */
@Repository
class ConsultaDeAuditoriaJdbc implements ConsultaDeAuditoria {

    private static final TypeReference<Map<String, Object>> MAPA = new TypeReference<>() {
    };

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    ConsultaDeAuditoriaJdbc(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public Map<UUID, EventoRegistrado> buscarUltimosPorActor(ConsultorioId consultorio, String entidad,
            Collection<UUID> entidades, ActorTipo actor) {
        Map<UUID, EventoRegistrado> ultimos = new LinkedHashMap<>();
        if (entidades.isEmpty()) {
            return ultimos;
        }
        jdbc.sql("""
                SELECT DISTINCT ON (entidad_id) entidad_id, accion, ocurrido_at, valor_anterior::text AS anterior,
                       valor_nuevo::text AS nuevo
                  FROM evento_auditoria
                 WHERE consultorio_id = :consultorio AND entidad = :entidad AND entidad_id IN (:entidades)
                   AND actor_tipo = :actor
                 ORDER BY entidad_id, ocurrido_at DESC, id DESC""")
                .param("consultorio", consultorio.valor())
                .param("entidad", entidad)
                .param("entidades", entidades)
                .param("actor", actor.name())
                .query((fila, numero) -> ultimos.put(fila.getObject("entidad_id", UUID.class), evento(fila)))
                .list();
        return ultimos;
    }

    @Override
    public Optional<EventoRegistrado> buscarUltimo(ConsultorioId consultorio, String entidad, UUID entidadId,
            ActorTipo actor, String accion) {
        return jdbc.sql("""
                SELECT accion, ocurrido_at, valor_anterior::text AS anterior, valor_nuevo::text AS nuevo
                  FROM evento_auditoria
                 WHERE consultorio_id = :consultorio AND entidad = :entidad AND entidad_id = :entidadId
                   AND actor_tipo = :actor AND accion = :accion
                 ORDER BY ocurrido_at DESC, id DESC
                 LIMIT 1""")
                .param("consultorio", consultorio.valor())
                .param("entidad", entidad)
                .param("entidadId", entidadId)
                .param("actor", actor.name())
                .param("accion", accion)
                .query((fila, numero) -> evento(fila))
                .optional();
    }

    private EventoRegistrado evento(ResultSet fila) throws SQLException {
        return new EventoRegistrado(fila.getString("accion"), fila.getTimestamp("ocurrido_at").toInstant(),
                mapa(fila.getString("anterior")), mapa(fila.getString("nuevo")));
    }

    private Map<String, Object> mapa(String valor) {
        if (valor == null) {
            return Map.of();
        }
        try {
            return json.readValue(valor, MAPA);
        } catch (JsonProcessingException e) {
            // Sin la causa ni el valor: puede contener datos personales.
            throw new IllegalStateException("No se pudieron leer los valores de un evento de auditoría");
        }
    }
}
