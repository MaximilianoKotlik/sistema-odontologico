package ar.sistemaodontologico.reserva.domain;

import java.time.Duration;
import java.time.Instant;

/** Hasta cuándo el paciente puede cancelar o reprogramar su turno (RN-RP-04): el límite exacto todavía se puede. */
public final class PlazoDeModificacion {

    private PlazoDeModificacion() {
    }

    public static Instant limite(Instant inicio, int anticipacionMin) {
        return inicio.minus(Duration.ofMinutes(anticipacionMin));
    }

    public static boolean permite(Instant inicio, int anticipacionMin, Instant ahora) {
        return !ahora.isAfter(limite(inicio, anticipacionMin));
    }
}
