package ar.sistemaodontologico.exportacion.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

/** Pacientes con su consentimiento y lo informado de DD-38 (C-19, design D3, aprobado por Maxi en #52). */
@Component
class ExportadorDePacientes extends ExportadorJdbc {

    ExportadorDePacientes(DataSource dataSource, ObjectMapper json) {
        super(dataSource, json, "pacientes", "paciente", "consultorio_id", "id", List.of("id", "nombre", "apellido", "dni",
                "telefono_e164", "email", "origen", "requiere_revision", "consentimiento_at", "consentimiento_version",
                "created_at", "nombre_informado", "apellido_informado", "email_informado", "informado_at",
                "informado_turno_id"));
    }
}
