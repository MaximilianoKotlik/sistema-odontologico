package ar.sistemaodontologico.agenda.web;

import ar.sistemaodontologico.agenda.application.BuscarPacientes;
import ar.sistemaodontologico.agenda.application.PacienteEncontrado;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/v1/pacientes?q=} (C-12, spec busqueda-de-pacientes, design D7). {@code q} puede ser un DNI o un
 * teléfono: no se loguea (RN-GL-01, tarea 4.2). La respuesta trae DNI y teléfono, así que la caché offline no la
 * guarda (tarea 4.3).
 */
@RestController
@RequestMapping("/api/v1/pacientes")
class ControladorDePacientes {

    record PacienteResponse(UUID id, String nombre, String apellido, String dni, String telefono,
            boolean requiereRevision) {

        static PacienteResponse de(PacienteEncontrado paciente) {
            return new PacienteResponse(paciente.id(), paciente.nombre(), paciente.apellido(), paciente.dni(),
                    paciente.telefono(), paciente.requiereRevision());
        }

        @Override
        public String toString() {
            return "PacienteResponse[id=" + id + "]";
        }
    }

    private final BuscarPacientes buscar;

    ControladorDePacientes(BuscarPacientes buscar) {
        this.buscar = buscar;
    }

    @GetMapping
    List<PacienteResponse> buscar(@RequestParam(defaultValue = "") String q) {
        return buscar.buscar(q).stream().map(PacienteResponse::de).toList();
    }
}
