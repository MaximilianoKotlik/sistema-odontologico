package ar.sistemaodontologico.compartido.application;

import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import ar.sistemaodontologico.compartido.domain.auditoria.ActorTipo;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de lectura del registro de auditoría para otros módulos (C-12, design D3). Vive en {@code compartido}, como
 * {@link PublicadorDeAuditoria}, porque ningún módulo depende de {@code auditoria} (regla {@code auditoriaSoloPorElPuerto}
 * de C-03); lo implementa {@code auditoria.infrastructure}. Todo filtra por consultorio (regla dura 6).
 */
public interface ConsultaDeAuditoria {

    /** Un evento ya registrado, con sus valores tal como se guardaron. */
    record EventoRegistrado(String accion, Instant ocurridoAt, Map<String, Object> valorAnterior,
            Map<String, Object> valorNuevo) {

        public EventoRegistrado {
            Objects.requireNonNull(accion, "accion");
            Objects.requireNonNull(ocurridoAt, "ocurridoAt");
            valorAnterior = valorAnterior == null ? Map.of() : valorAnterior;
            valorNuevo = valorNuevo == null ? Map.of() : valorNuevo;
        }
    }

    /** El último evento de ese tipo de actor por cada entidad pedida; las que no tienen ninguno no aparecen. */
    Map<UUID, EventoRegistrado> buscarUltimosPorActor(ConsultorioId consultorio, String entidad,
            Collection<UUID> entidades, ActorTipo actor);

    /** El último evento de esa acción y ese tipo de actor sobre la entidad. */
    Optional<EventoRegistrado> buscarUltimo(ConsultorioId consultorio, String entidad, UUID entidadId,
            ActorTipo actor, String accion);
}
