package ar.sistemaodontologico.compartido.domain;

import java.util.Objects;
import java.util.UUID;

/** Identificador del consultorio (tenant). Todo acceso a datos de negocio lo recibe explícito. */
public record ConsultorioId(UUID valor) {

    public ConsultorioId {
        Objects.requireNonNull(valor, "valor");
    }

    @Override
    public String toString() {
        return valor.toString();
    }
}
