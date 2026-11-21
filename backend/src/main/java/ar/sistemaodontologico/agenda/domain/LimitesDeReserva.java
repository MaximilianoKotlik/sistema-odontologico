package ar.sistemaodontologico.agenda.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Límites de la reserva pública (RN-RP-01): no antes de {@code ahora + anticipacionMin} ni después de
 * {@code ahora + horizonteDias × 24 h}. {@code ahora} sale del reloj del sistema, por eso entra como dato.
 */
public record LimitesDeReserva(int anticipacionMin, int horizonteDias, Instant ahora) {

    public LimitesDeReserva {
        Objects.requireNonNull(ahora, "ahora");
        if (anticipacionMin < 0 || horizonteDias < 1) {
            throw new IllegalArgumentException("Anticipación no negativa y horizonte de al menos un día");
        }
    }

    boolean admite(Instant inicio) {
        Instant primero = ahora.plusSeconds(anticipacionMin * 60L);
        Instant ultimo = ahora.plusSeconds(horizonteDias * 86_400L);
        return !inicio.isBefore(primero) && !inicio.isAfter(ultimo);
    }
}
