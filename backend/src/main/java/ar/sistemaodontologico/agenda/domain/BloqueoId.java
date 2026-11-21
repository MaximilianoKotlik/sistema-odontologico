package ar.sistemaodontologico.agenda.domain;

import java.util.Objects;
import java.util.UUID;

/** Identificador de un bloqueo (UUIDv7 generado en la aplicación). */
public record BloqueoId(UUID valor) {

    public BloqueoId {
        Objects.requireNonNull(valor, "valor");
    }
}
