package ar.sistemaodontologico.reserva.application;

import java.time.Instant;
import java.util.UUID;

/** Respuesta de una reserva: la única vez que el token sale en claro (RN-RP-06). {@link #toString()} no lo incluye. */
public record ReservaCreada(TurnoReservado turno, String linkGestion) {

    public record TurnoReservado(UUID id, Instant inicio, Instant fin, String prestacion, String estado) {
    }

    @Override
    public String toString() {
        return "ReservaCreada[turno=" + turno.id() + "]";
    }
}
