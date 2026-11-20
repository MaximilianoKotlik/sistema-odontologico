package ar.sistemaodontologico.compartido.domain.auditoria;

import java.time.Instant;
import java.util.Objects;

/**
 * Evento publicado dentro de la transacción del cambio. C-03 lo persiste en {@code evento_auditoria} con un
 * {@code @TransactionalEventListener(phase = BEFORE_COMMIT)} (design D10).
 */
public record EventoDeAuditoria(Instant ocurridoAt, CambioAuditado cambio) {

    public EventoDeAuditoria {
        Objects.requireNonNull(ocurridoAt, "ocurridoAt");
        Objects.requireNonNull(cambio, "cambio");
    }
}
