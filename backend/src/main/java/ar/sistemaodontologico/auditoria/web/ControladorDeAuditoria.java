package ar.sistemaodontologico.auditoria.web;

import ar.sistemaodontologico.auditoria.application.ConsultarAuditoria;
import ar.sistemaodontologico.auditoria.application.LecturaDeAuditoria.ActorLeido;
import ar.sistemaodontologico.auditoria.application.LecturaDeAuditoria.ContextoDeTurno;
import ar.sistemaodontologico.auditoria.application.LecturaDeAuditoria.EventoLeido;
import ar.sistemaodontologico.auditoria.domain.FiltrosDeAuditoria;
import io.swagger.v3.oas.annotations.Operation;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Historial de un turno y registro del consultorio (C-18, US-040, design D1). Los filtros llegan como texto para que un
 * valor mal formado responda 400 {@code VALIDACION} con su campo.
 */
@RestController
@RequestMapping("/api/v1")
class ControladorDeAuditoria {

    record ActorResponse(String tipo, UUID id, String nombre, boolean identidadEnRevision) {

        static ActorResponse de(ActorLeido actor) {
            return new ActorResponse(actor.tipo(), actor.id(), actor.nombre(), actor.identidadEnRevision());
        }
    }

    record ContextoResponse(Instant inicio, String paciente) {

        static ContextoResponse de(ContextoDeTurno contexto) {
            return contexto == null ? null : new ContextoResponse(contexto.inicio(), contexto.paciente());
        }
    }

    record EventoResponse(UUID id, Instant ocurridoAt, ActorResponse actor, String accion, String entidad,
            UUID entidadId, Map<String, Object> valorAnterior, Map<String, Object> valorNuevo, String ip,
            ContextoResponse contexto) {

        static EventoResponse de(EventoLeido evento) {
            return new EventoResponse(evento.id(), evento.ocurridoAt(), ActorResponse.de(evento.actor()),
                    evento.accion(), evento.entidad(), evento.entidadId(), evento.valorAnterior(), evento.valorNuevo(),
                    evento.ip(), ContextoResponse.de(evento.contexto()));
        }
    }

    record RegistroDeCambiosResponse(List<EventoResponse> eventos, String siguienteCursor) {
    }

    private final ConsultarAuditoria consultar;

    ControladorDeAuditoria(ConsultarAuditoria consultar) {
        this.consultar = consultar;
    }

    @GetMapping("/turnos/{id}/historial")
    @Operation(summary = "Eventos de un turno, en orden cronológico")
    List<EventoResponse> historial(@PathVariable UUID id) {
        return consultar.historialDeTurno(id).stream().map(EventoResponse::de).toList();
    }

    @GetMapping("/auditoria")
    @Operation(summary = "Registro de cambios del consultorio, del más nuevo al más viejo")
    RegistroDeCambiosResponse registro(@RequestParam(required = false) String turnoId,
            @RequestParam(required = false) String desde, @RequestParam(required = false) String hasta,
            @RequestParam(required = false) List<String> accion, @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String tamano) {
        FiltrosDeAuditoria filtros = FiltrosDeAuditoria.desdeTexto(turnoId, desde, hasta, accion);
        ConsultarAuditoria.PaginaDeAuditoria pagina = consultar.registro(filtros, cursor, tamano);
        return new RegistroDeCambiosResponse(pagina.eventos().stream().map(EventoResponse::de).toList(),
                pagina.siguienteCursor());
    }
}
