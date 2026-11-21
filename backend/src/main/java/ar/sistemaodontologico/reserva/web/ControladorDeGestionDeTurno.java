package ar.sistemaodontologico.reserva.web;

import ar.sistemaodontologico.reserva.application.GestionarTurnoPorToken;
import ar.sistemaodontologico.reserva.application.HuecosPublicos;
import ar.sistemaodontologico.reserva.application.VistaDeTurno;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Gestión del turno con el token del link (spec gestion-turno-paciente). Todo token inválido responde el mismo 404. */
@RestController
@RequestMapping("/api/v1/public/turnos/{token}")
@Tag(name = "Gestión del turno por link")
class ControladorDeGestionDeTurno {

    private final GestionarTurnoPorToken gestion;

    ControladorDeGestionDeTurno(GestionarTurnoPorToken gestion) {
        this.gestion = gestion;
    }

    record PedidoDeReprogramacion(@NotNull Instant nuevoInicio) {
    }

    @GetMapping
    @Operation(summary = "Ver el turno")
    @ApiResponse(responseCode = "200", description = "Turno, paciente, consultorio y acciones disponibles")
    @ApiResponse(responseCode = "404", description = "RECURSO_NO_ENCONTRADO")
    @ApiResponse(responseCode = "429", description = "DEMASIADAS_SOLICITUDES")
    VistaDeTurno ver(@PathVariable String token) {
        return gestion.ver(token);
    }

    @GetMapping("/huecos")
    @Operation(summary = "Huecos para reprogramar, sin contar el propio turno")
    @ApiResponse(responseCode = "200", description = "Días con sus huecos en UTC")
    @ApiResponse(responseCode = "400", description = "VALIDACION")
    @ApiResponse(responseCode = "404", description = "RECURSO_NO_ENCONTRADO")
    @ApiResponse(responseCode = "409", description = "RESERVA_NO_DISPONIBLE, con telefonoContacto")
    @ApiResponse(responseCode = "429", description = "DEMASIADAS_SOLICITUDES")
    HuecosPublicos huecos(@PathVariable String token,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return gestion.huecos(token, desde, hasta);
    }

    @PostMapping("/reprogramacion")
    @Operation(summary = "Mover el turno a otro hueco")
    @ApiResponse(responseCode = "200", description = "Turno con el horario nuevo")
    @ApiResponse(responseCode = "400", description = "VALIDACION")
    @ApiResponse(responseCode = "404", description = "RECURSO_NO_ENCONTRADO")
    @ApiResponse(responseCode = "409", description = "FUERA_DE_PLAZO o RESERVA_NO_DISPONIBLE (con telefonoContacto), "
            + "HUECO_NO_DISPONIBLE (con huecos) o CONFLICTO_CONCURRENCIA")
    @ApiResponse(responseCode = "413", description = "CUERPO_DEMASIADO_GRANDE")
    @ApiResponse(responseCode = "429", description = "DEMASIADAS_SOLICITUDES")
    VistaDeTurno reprogramar(@PathVariable String token, @Valid @RequestBody PedidoDeReprogramacion pedido,
            HttpServletRequest peticion) {
        return gestion.reprogramar(token, pedido.nuevoInicio(), Optional.ofNullable(peticion.getRemoteAddr()));
    }

    @PostMapping("/cancelacion")
    @Operation(summary = "Cancelar el turno")
    @ApiResponse(responseCode = "200", description = "Turno CANCELADO")
    @ApiResponse(responseCode = "404", description = "RECURSO_NO_ENCONTRADO")
    @ApiResponse(responseCode = "409", description = "FUERA_DE_PLAZO (con telefonoContacto) o CONFLICTO_CONCURRENCIA")
    @ApiResponse(responseCode = "429", description = "DEMASIADAS_SOLICITUDES")
    VistaDeTurno cancelar(@PathVariable String token, HttpServletRequest peticion) {
        return gestion.cancelar(token, Optional.ofNullable(peticion.getRemoteAddr()));
    }
}
