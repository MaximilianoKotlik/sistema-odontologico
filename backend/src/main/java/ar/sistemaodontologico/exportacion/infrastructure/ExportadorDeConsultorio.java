package ar.sistemaodontologico.exportacion.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

/** Todo lo configurable del consultorio, más su plan (C-19, design D3). */
@Component
class ExportadorDeConsultorio extends ExportadorJdbc {

    ExportadorDeConsultorio(DataSource dataSource, ObjectMapper json) {
        super(dataSource, json, "consultorio", "consultorio", "id", "id", List.of("nombre", "slug", "direccion",
                "telefono_contacto", "zona_horaria", "anticipacion_cancelacion_min", "anticipacion_reserva_min",
                "horizonte_reserva_dias", "paso_huecos_min", "reserva_publica_activa", "plan", "prueba_hasta",
                "created_at"));
    }
}
