package ar.sistemaodontologico.agenda.domain;

import java.util.Objects;
import java.util.UUID;

/** Identificador de un turno (UUIDv7 generado en la aplicación). */
public record TurnoId(UUID valor) {

    public TurnoId {
        Objects.requireNonNull(valor, "valor");
    }
}
