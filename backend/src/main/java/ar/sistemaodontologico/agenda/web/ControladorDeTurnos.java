package ar.sistemaodontologico.agenda.web;

import ar.sistemaodontologico.agenda.application.AjustarDuracion;
import ar.sistemaodontologico.agenda.application.Autor;
import ar.sistemaodontologico.agenda.application.CambiarEstadoTurno;
import ar.sistemaodontologico.agenda.application.CargarTurno;
import ar.sistemaodontologico.agenda.application.ConsultarAgenda;
import ar.sistemaodontologico.agenda.application.DatosDePaciente;
import ar.sistemaodontologico.agenda.application.EditarNota;
import ar.sistemaodontologico.agenda.application.MarcarNovedadVista;
import ar.sistemaodontologico.agenda.application.ReprogramarTurno;
import ar.sistemaodontologico.agenda.application.TurnoGuardado;
import ar.sistemaodontologico.agenda.domain.PacienteId;
import ar.sistemaodontologico.agenda.domain.TurnoId;
import ar.sistemaodontologico.agenda.web.RespuestasDeAgenda.DetalleResponse;
import ar.sistemaodontologico.agenda.web.RespuestasDeAgenda.TurnoResponse;
import ar.sistemaodontologico.compartido.application.SesionActual;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/v1/turnos} (C-12, specs agenda-del-odontologo y api-de-turnos-y-bloqueos; design D4, D5, D9 y D10). Las
 * escrituras son los casos de uso de C-11 con el odontólogo de la sesión como actor; responden el turno con la forma
 * del detalle y su {@code version} nueva. Todo turno de otro consultorio es 404 (RN-TE-04, regla dura 6).
 *
 * <p>Bean Validation solo cubre lo estructural (requeridos); las reglas de dominio responden con sus códigos (D10).
 */
@RestController
@RequestMapping("/api/v1/turnos")
class ControladorDeTurnos {

    /** Lo escrito en el alta rápida, tal como llega: el dominio lo normaliza (DNI con puntos, teléfono local). */
    record PacienteNuevoRequest(@NotBlank String nombre, @NotBlank String apellido, @NotBlank String dni,
            @NotBlank String telefono, String email) {

        DatosDePaciente aDatos() {
            return new DatosDePaciente(nombre, apellido, dni, telefono, email);
        }

        @Override
        public String toString() {
            return "PacienteNuevoRequest[***]";
        }
    }

    /** Exactamente uno de {@code pacienteId} o {@code pacienteNuevo}. */
    record CrearTurnoRequest(UUID pacienteId, @Valid PacienteNuevoRequest pacienteNuevo, @NotNull UUID prestacionId,
            @NotNull Instant inicio, Integer duracionMin, boolean esSobreturno) {

        CargarTurno.Pedido aPedido() {
            return new CargarTurno.Pedido(Optional.ofNullable(pacienteId).map(PacienteId::new),
                    Optional.ofNullable(pacienteNuevo).map(PacienteNuevoRequest::aDatos), prestacionId, inicio,
                    Optional.ofNullable(duracionMin), esSobreturno);
        }
    }

    record TurnoCreadoResponse(DetalleResponse turno, boolean pacienteReutilizado) {
    }

    enum AccionDeEstado {
        CONFIRMAR,
        CANCELAR,
        MARCAR_AUSENTE
    }

    /** {@code motivo} solo al cancelar; no va a la auditoría (C-11 D8). */
    record CambiarEstadoRequest(@NotNull AccionDeEstado accion, String motivo, @NotNull Long version) {
    }

    record ReprogramarRequest(@NotNull Instant inicio, @NotNull Long version) {
    }

    record DuracionRequest(@NotNull Integer duracionMin, @NotNull Long version) {
    }

    /** Una nota vacía o {@code null} la borra. */
    record NotaRequest(String nota, @NotNull Long version) {
    }

    /** {@code novedadAt} es el {@code novedadPacienteAt} que el cliente recibió (D5). */
    record VistoRequest(@NotNull Instant novedadAt) {
    }

    private final ConsultarAgenda consultar;
    private final MarcarNovedadVista visto;
    private final CargarTurno cargar;
    private final CambiarEstadoTurno estados;
    private final ReprogramarTurno reprogramar;
    private final AjustarDuracion duraciones;
    private final EditarNota notas;
    private final SesionActual sesion;

    ControladorDeTurnos(ConsultarAgenda consultar, MarcarNovedadVista visto, CargarTurno cargar,
            CambiarEstadoTurno estados, ReprogramarTurno reprogramar, AjustarDuracion duraciones, EditarNota notas,
            SesionActual sesion) {
        this.consultar = consultar;
        this.visto = visto;
        this.cargar = cargar;
        this.estados = estados;
        this.reprogramar = reprogramar;
        this.duraciones = duraciones;
        this.notas = notas;
        this.sesion = sesion;
    }

    @GetMapping("/fuera-de-disponibilidad")
    List<TurnoResponse> fueraDeDisponibilidad() {
        return consultar.fueraDeDisponibilidad().stream().map(TurnoResponse::de).toList();
    }

    @GetMapping("/{id}")
    DetalleResponse detalle(@PathVariable UUID id) {
        return detalleDe(new TurnoId(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    TurnoCreadoResponse crear(@Valid @RequestBody CrearTurnoRequest pedido) {
        CargarTurno.Resultado resultado = cargar.cargar(pedido.aPedido(), odontologo());
        return new TurnoCreadoResponse(detalleDe(resultado.turno().turno().id()), resultado.pacienteReutilizado());
    }

    @PatchMapping("/{id}/estado")
    DetalleResponse cambiarEstado(@PathVariable UUID id, @Valid @RequestBody CambiarEstadoRequest pedido) {
        TurnoId turno = new TurnoId(id);
        long version = pedido.version();
        return detalleDe(switch (pedido.accion()) {
            case CONFIRMAR -> estados.confirmar(turno, version, odontologo());
            case CANCELAR -> estados.cancelar(turno, version, pedido.motivo(), odontologo());
            case MARCAR_AUSENTE -> estados.marcarAusente(turno, version, odontologo());
        });
    }

    /** Sin anticipación mínima para el odontólogo; conserva el token de gestión del paciente (C-11). */
    @PostMapping("/{id}/reprogramar")
    DetalleResponse reprogramar(@PathVariable UUID id, @Valid @RequestBody ReprogramarRequest pedido) {
        return detalleDe(reprogramar.reprogramar(new TurnoId(id), pedido.version(), pedido.inicio(), odontologo()));
    }

    @PatchMapping("/{id}/duracion")
    DetalleResponse duracion(@PathVariable UUID id, @Valid @RequestBody DuracionRequest pedido) {
        return detalleDe(duraciones.ajustar(new TurnoId(id), pedido.version(), pedido.duracionMin(), odontologo()));
    }

    @PatchMapping("/{id}/nota")
    DetalleResponse nota(@PathVariable UUID id, @Valid @RequestBody NotaRequest pedido) {
        return detalleDe(notas.editar(new TurnoId(id), pedido.version(), pedido.nota(), odontologo()));
    }

    @PostMapping("/{id}/visto")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void visto(@PathVariable UUID id, @Valid @RequestBody VistoRequest pedido) {
        visto.marcar(new TurnoId(id), pedido.novedadAt());
    }

    private DetalleResponse detalleDe(TurnoGuardado guardado) {
        return detalleDe(guardado.turno().id());
    }

    private DetalleResponse detalleDe(TurnoId id) {
        return DetalleResponse.de(consultar.detalle(id));
    }

    private Autor odontologo() {
        return Autor.odontologo(sesion.usuarioId(), sesion.ip());
    }
}
