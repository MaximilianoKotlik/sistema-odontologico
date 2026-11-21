package ar.sistemaodontologico.exportacion.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

/** Prestaciones, también las desactivadas (C-19, design D3). */
@Component
class ExportadorDePrestaciones extends ExportadorJdbc {

    ExportadorDePrestaciones(DataSource dataSource, ObjectMapper json) {
        super(dataSource, json, "prestaciones", "tipo_prestacion", "consultorio_id", "orden, id", List.of("id", "nombre",
                "duracion_min", "activa", "visible_publica", "orden", "created_at", "updated_at"));
    }
}
