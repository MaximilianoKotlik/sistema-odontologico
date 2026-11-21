package ar.sistemaodontologico.exportacion.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

/** Datos del odontólogo, sin credenciales ni estado de bloqueo (C-19, design D3; RN-EX-02). */
@Component
class ExportadorDeOdontologo extends ExportadorJdbc {

    ExportadorDeOdontologo(DataSource dataSource, ObjectMapper json) {
        super(dataSource, json, "odontologo", "usuario", "consultorio_id", "id", List.of("email", "nombre", "apellido",
                "matricula", "terminos_version", "terminos_aceptados_at", "created_at"));
    }
}
