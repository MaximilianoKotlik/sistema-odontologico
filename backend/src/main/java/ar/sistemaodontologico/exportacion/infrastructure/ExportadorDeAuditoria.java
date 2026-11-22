package ar.sistemaodontologico.exportacion.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

/** Registro de auditoría completo, del más viejo al más nuevo (C-19, design D3). */
@Component
class ExportadorDeAuditoria extends ExportadorJdbc {

    ExportadorDeAuditoria(DataSource dataSource, ObjectMapper json) {
        super(dataSource, json, "auditoria", "evento_auditoria", "consultorio_id", "ocurrido_at, id", List.of("id",
                "ocurrido_at", "actor_tipo", "actor_id", "entidad", "entidad_id", "accion", "valor_anterior",
                "valor_nuevo", "ip"));
    }
}
