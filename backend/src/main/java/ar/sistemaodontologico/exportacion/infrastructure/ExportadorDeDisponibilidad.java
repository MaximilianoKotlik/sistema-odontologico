package ar.sistemaodontologico.exportacion.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

/** Franjas semanales de atención, en hora local del consultorio (C-19, design D3). */
@Component
class ExportadorDeDisponibilidad extends ExportadorJdbc {

    ExportadorDeDisponibilidad(DataSource dataSource, ObjectMapper json) {
        super(dataSource, json, "disponibilidad", "disponibilidad", "consultorio_id", "dia_semana, hora_inicio",
                List.of("dia_semana", "hora_inicio", "hora_fin"));
    }
}
