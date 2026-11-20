package ar.sistemaodontologico.arquitectura.fixtures.reloj;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

/** Fixture válido: toma la hora del Clock inyectado. */
public class CalculoConClock {

    private final Clock reloj;

    public CalculoConClock(Clock reloj) {
        this.reloj = reloj;
    }

    public Instant ahora() {
        return Instant.now(reloj);
    }

    public LocalDate hoy() {
        return LocalDate.now(reloj);
    }
}
