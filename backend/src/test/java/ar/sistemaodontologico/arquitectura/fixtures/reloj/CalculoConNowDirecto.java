package ar.sistemaodontologico.arquitectura.fixtures.reloj;

import java.time.Instant;
import java.time.LocalDate;

/** Fixture que viola la regla del reloj: toma la hora del sistema sin pasar por el Clock inyectado. */
public class CalculoConNowDirecto {

    public Instant ahora() {
        return Instant.now();
    }

    public LocalDate hoy() {
        return LocalDate.now();
    }
}
