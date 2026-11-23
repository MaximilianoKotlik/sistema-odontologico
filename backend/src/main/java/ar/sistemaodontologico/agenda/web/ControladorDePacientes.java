package ar.sistemaodontologico.agenda.web;

import ar.sistemaodontologico.agenda.application.Autor;
import ar.sistemaodontologico.agenda.application.BuscarPacientes;
import ar.sistemaodontologico.agenda.application.ConsultarPacientes;
import ar.sistemaodontologico.agenda.application.DatosCorregidos;
import ar.sistemaodontologico.agenda.application.PacienteDeLaLista;
import ar.sistemaodontologico.agenda.application.PacienteEncontrado;
import ar.sistemaodontologico.agenda.application.PaginaDePacientes;
import ar.sistemaodontologico.agenda.application.RevisarPaciente;
import ar.sistemaodontologico.agenda.domain.PacienteId;
import ar.sistemaodontologico.compartido.application.SesionActual;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/v1/pacientes?q=} (C-12, spec busqueda-de-pacientes, design D7) y la lista paginada y el detalle de
 * C-17 (spec revision-pacientes, design D4 y D10). {@code q} puede ser un DNI o un teléfono: no se loguea
 * (RN-GL-01, tarea 4.2). Las respuestas traen DNI y teléfono, así que la caché offline no las guarda (tarea 4.3).
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

    record PaginaDePacientesResponse(List<PacienteDeLaLista> items, long total) {

        static PaginaDePacientesResponse de(PaginaDePacientes pagina) {
            return new PaginaDePacientesResponse(pagina.items(), pagina.total());
        }
    }

    /** El {@code informadoAt} del detalle que se mostró (design D9). */
    record ConfirmacionDeRevisionRequest(@NotNull Instant informadoAt) {
    }

    /** El DNI y el teléfono no se corrigen: si vienen en el cuerpo, se ignoran. La validación es la del dominio. */
    record CorreccionRequest(@NotNull Instant informadoAt, String nombre, String apellido, String email) {

        @Override
        public String toString() {
            return "CorreccionRequest[informadoAt=" + informadoAt + "]";
        }
    }

    private final BuscarPacientes buscar;
    private final ConsultarPacientes consultar;
    private final RevisarPaciente revisar;
    private final SesionActual sesion;

    ControladorDePacientes(BuscarPacientes buscar, ConsultarPacientes consultar, RevisarPaciente revisar,
            SesionActual sesion) {
        this.buscar = buscar;
        this.consultar = consultar;
        this.revisar = revisar;
        this.sesion = sesion;
    }

    @GetMapping
    List<PacienteResponse> buscar(@RequestParam(defaultValue = "") String q) {
        return buscar.buscar(q).stream().map(PacienteResponse::de).toList();
    }

    @GetMapping("/listado")
    PaginaDePacientesResponse listar(@RequestParam(defaultValue = "false") boolean requiereRevision,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + ConsultarPacientes.TAMANO_POR_DEFECTO) int size) {
        return PaginaDePacientesResponse.de(consultar.listar(requiereRevision, page, size));
    }

    @GetMapping("/{id}")
    PacienteDeLaLista detalle(@PathVariable UUID id) {
        return consultar.detalle(id);
    }

    @PostMapping("/{id}/confirmar-revision")
    PacienteDeLaLista confirmarRevision(@PathVariable UUID id,
            @Valid @RequestBody ConfirmacionDeRevisionRequest pedido) {
        revisar.confirmar(new PacienteId(id), pedido.informadoAt(), odontologo());
        return consultar.detalle(id);
    }

    @PatchMapping("/{id}")
    PacienteDeLaLista corregir(@PathVariable UUID id, @Valid @RequestBody CorreccionRequest pedido) {
        revisar.corregir(new PacienteId(id), pedido.informadoAt(),
                new DatosCorregidos(pedido.nombre(), pedido.apellido(), pedido.email()), odontologo());
        return consultar.detalle(id);
    }

    private Autor odontologo() {
        return Autor.odontologo(sesion.usuarioId(), sesion.ip());
    }
}
