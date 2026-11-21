package ar.sistemaodontologico.reserva.web;

import ar.sistemaodontologico.agenda.application.DatosDePaciente;
import ar.sistemaodontologico.reserva.application.ConsultarHuecosPublicos;
import ar.sistemaodontologico.reserva.application.ConsultarPaginaPublica;
import ar.sistemaodontologico.reserva.application.HuecosPublicos;
import ar.sistemaodontologico.reserva.application.PaginaPublica;
import ar.sistemaodontologico.reserva.application.ReservaCreada;
import ar.sistemaodontologico.reserva.application.ReservarTurnoPublico;
import ar.sistemaodontologico.reserva.application.ReservarTurnoPublico.PedidoDeReserva;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Página, huecos y reserva sin cuenta (spec reserva-publica). El consultorio sale solo del slug de la ruta. */
@RestController
@RequestMapping("/api/v1/public/consultorios/{slug}")
@Tag(name = "Reserva pública")
class ControladorDeReservaPublica {

    private final ConsultarPaginaPublica pagina;
    private final ConsultarHuecosPublicos huecos;
    private final ReservarTurnoPublico reservar;

    ControladorDeReservaPublica(ConsultarPaginaPublica pagina, ConsultarHuecosPublicos huecos,
            ReservarTurnoPublico reservar) {
        this.pagina = pagina;
        this.huecos = huecos;
        this.reservar = reservar;
    }

    /** Sin email ni datos de salud obligatorios. Los datos personales no aparecen en {@link #toString()}. */
    record PedidoDeReservaDto(@NotNull UUID prestacionId, @NotNull Instant inicio, @NotBlank String nombre,
            @NotBlank String apellido, @NotBlank String dni, @NotBlank String telefono, String email,
            @AssertTrue boolean aceptaPrivacidad) {

        @Override
        public String toString() {
            return "PedidoDeReservaDto[***]";
        }
    }

    @GetMapping
    @Operation(summary = "Página pública del consultorio")
    @ApiResponse(responseCode = "200", description = "Datos públicos; sin reserva online, sin prestaciones")
    @ApiResponse(responseCode = "404", description = "RECURSO_NO_ENCONTRADO")
    @ApiResponse(responseCode = "429", description = "DEMASIADAS_SOLICITUDES")
    PaginaPublica pagina(@PathVariable String slug) {
        return pagina.consultar(slug);
    }

    @GetMapping("/huecos")
    @Operation(summary = "Huecos ofrecibles para una prestación, hasta 31 días")
    @ApiResponse(responseCode = "200", description = "Días con sus huecos en UTC")
    @ApiResponse(responseCode = "400", description = "VALIDACION")
    @ApiResponse(responseCode = "404", description = "RECURSO_NO_ENCONTRADO")
    @ApiResponse(responseCode = "409", description = "RESERVA_NO_DISPONIBLE, con telefonoContacto")
    @ApiResponse(responseCode = "429", description = "DEMASIADAS_SOLICITUDES")
    HuecosPublicos huecos(@PathVariable String slug, @RequestParam UUID prestacionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return huecos.consultar(slug, prestacionId, desde, hasta);
    }

    @PostMapping("/turnos")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Reservar un turno sin cuenta; el link de gestión se devuelve una sola vez")
    @ApiResponse(responseCode = "201", description = "Turno RESERVADO con linkGestion")
    @ApiResponse(responseCode = "400", description = "VALIDACION, DNI_INVALIDO o TELEFONO_INVALIDO")
    @ApiResponse(responseCode = "404", description = "RECURSO_NO_ENCONTRADO")
    @ApiResponse(responseCode = "409",
            description = "HUECO_NO_DISPONIBLE (con huecos), LIMITE_TURNOS o RESERVA_NO_DISPONIBLE (con telefonoContacto)")
    @ApiResponse(responseCode = "413", description = "CUERPO_DEMASIADO_GRANDE")
    @ApiResponse(responseCode = "429", description = "DEMASIADAS_SOLICITUDES")
    ReservaCreada reservar(@PathVariable String slug, @Valid @RequestBody PedidoDeReservaDto pedido,
            HttpServletRequest peticion) {
        DatosDePaciente datos = new DatosDePaciente(pedido.nombre(), pedido.apellido(), pedido.dni(), pedido.telefono(),
                pedido.email());
        return reservar.reservar(slug, new PedidoDeReserva(pedido.prestacionId(), pedido.inicio(), datos),
                Optional.ofNullable(peticion.getRemoteAddr()));
    }
}
