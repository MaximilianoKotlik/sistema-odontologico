package ar.sistemaodontologico.auditoria.infrastructure;

import ar.sistemaodontologico.auditoria.application.RepositorioDeAuditoria;
import ar.sistemaodontologico.auditoria.domain.RegistroDeAuditoria;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Inserta en {@code evento_auditoria} con JdbcClient, sin entidad JPA: un evento solo se inserta (design D-2).
 * Comparte la transacción del cambio auditado porque usa el mismo {@code DataSource}.
 */
@Repository
class RepositorioDeAuditoriaJdbc implements RepositorioDeAuditoria {

    private final JdbcClient jdbc;
    private final ObjectMapper json;
    private final EntityManager entidades;

    RepositorioDeAuditoriaJdbc(JdbcClient jdbc, ObjectMapper json, EntityManager entidades) {
        this.jdbc = jdbc;
        this.json = json;
        this.entidades = entidades;
    }

    @Override
    public void guardar(RegistroDeAuditoria registro) {
        // El listener corre en BEFORE_COMMIT, antes del flush de Hibernate: sin esto, un INSERT por JDBC no ve
        // las filas que el caso de uso creó con JPA y la FK del evento falla (design D-3).
        entidades.flush();
        jdbc.sql("""
                INSERT INTO evento_auditoria (id, consultorio_id, ocurrido_at, actor_tipo, actor_id, entidad,
                    entidad_id, accion, valor_anterior, valor_nuevo, ip)
                VALUES (:id, :consultorio, :ocurrido, :actorTipo, :actorId, :entidad, :entidadId, :accion,
                    CAST(:anterior AS jsonb), CAST(:nuevo AS jsonb), CAST(:ip AS inet))""")
                .param("id", registro.id())
                .param("consultorio", registro.consultorioId())
                .param("ocurrido", Timestamp.from(registro.ocurridoAt()))
                .param("actorTipo", registro.actorTipo().name())
                .param("actorId", registro.actorId())
                .param("entidad", registro.entidad())
                .param("entidadId", registro.entidadId())
                .param("accion", registro.accion())
                .param("anterior", aJson(registro.valorAnterior()))
                .param("nuevo", aJson(registro.valorNuevo()))
                .param("ip", registro.ip())
                .update();
    }

    private String aJson(Map<String, Object> valores) {
        if (valores == null) {
            return null;
        }
        try {
            return json.writeValueAsString(valores);
        } catch (JsonProcessingException e) {
            // Sin la causa ni los valores: podrían contener datos personales.
            throw new IllegalStateException("No se pudieron serializar los valores del evento de auditoría");
        }
    }
}
