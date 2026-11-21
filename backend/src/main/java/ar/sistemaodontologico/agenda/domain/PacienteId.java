package ar.sistemaodontologico.agenda.domain;

import java.util.Objects;
import java.util.UUID;

/** Identificador de un paciente (UUIDv7 generado en la aplicación). */
public record PacienteId(UUID valor) {

    public PacienteId {
        Objects.requireNonNull(valor, "valor");
    }
}
