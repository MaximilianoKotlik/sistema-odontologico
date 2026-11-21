package ar.sistemaodontologico.auditoria.application;

import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Puerto de lectura del registro (C-18, design D1), separado de {@link RepositorioDeAuditoria}, que solo inserta: así la
 * lectura no puede escribir. Siempre recibe el consultorio (regla dura 6).
 */
public interface LecturaDeAuditoria {

    /**
     * Quién hizo el cambio. Con {@code identidadEnRevision}, el nombre es nulo: la reserva se hizo con el DNI de la ficha
     * y otro nombre, y el historial no puede afirmar que fue esa persona (design D3, DD-24 y DD-38).
     */
    record ActorLeido(String tipo, UUID id, String nombre, boolean identidadEnRevision) {
    }

    /** Estado actual del turno del evento: indica a quién está asignado, no quién hizo el cambio. */
    record ContextoDeTurno(Instant inicio, String paciente) {
    }

    /** Un evento con su actor y, si es de un turno que existe, su contexto. */
    record EventoLeido(UUID id, Instant ocurridoAt, ActorLeido actor, String accion, ContextoDeTurno contexto) {
    }

    /** Eventos del turno en orden cronológico; vacío si el turno no tiene eventos en este consultorio. */
    List<EventoLeido> listarDeTurno(ConsultorioId consultorio, UUID turnoId);
}
