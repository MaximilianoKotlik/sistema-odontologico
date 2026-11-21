package ar.sistemaodontologico.exportacion.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

/**
 * Turnos de cualquier estado y fecha, con los ids que los relacionan (C-19, design D3). Sin
 * {@code novedad_paciente_at}, que es una marca de lectura de la interfaz.
 */
@Component
class ExportadorDeTurnos extends ExportadorJdbc {

    ExportadorDeTurnos(DataSource dataSource, ObjectMapper json) {
        super(dataSource, json, "turnos", "turno", "consultorio_id", "inicio, id", List.of("id", "paciente_id",
                "tipo_prestacion_id", "inicio", "fin", "duracion_min", "estado", "es_sobreturno", "origen",
                "cancelado_por", "motivo_cancelacion", "nota_interna", "created_at", "updated_at"));
    }
}
