package ar.sistemaodontologico.compartido.application;

import java.time.Duration;
import java.util.Objects;

/**
 * Cupo de solicitudes de una operación: {@code capacidad} por cada {@code periodo}. El {@code nombre} separa los
 * cupos de operaciones distintas aunque compartan clave (por ejemplo, una misma IP en login y en registro).
 */
public record LimiteDeSolicitudes(String nombre, long capacidad, Duration periodo) {

    public LimiteDeSolicitudes {
        Objects.requireNonNull(nombre, "nombre");
        Objects.requireNonNull(periodo, "periodo");
        if (capacidad <= 0) {
            throw new IllegalArgumentException("La capacidad tiene que ser positiva");
        }
        if (periodo.isNegative() || periodo.isZero()) {
            throw new IllegalArgumentException("El período tiene que ser positivo");
        }
    }
}
