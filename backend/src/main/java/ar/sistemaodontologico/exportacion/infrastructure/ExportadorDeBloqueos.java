package ar.sistemaodontologico.exportacion.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

/** Bloqueos de la agenda (C-19, design D3). */
@Component
class ExportadorDeBloqueos extends ExportadorJdbc {

    ExportadorDeBloqueos(DataSource dataSource, ObjectMapper json) {
        super(dataSource, json, "bloqueos", "bloqueo", "consultorio_id", "inicio, id",
                List.of("id", "inicio", "fin", "motivo", "created_at"));
    }
}
