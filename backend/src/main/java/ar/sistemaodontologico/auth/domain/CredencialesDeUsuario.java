package ar.sistemaodontologico.auth.domain;

import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import java.util.UUID;

/** Lo que el login necesita de un usuario. */
public record CredencialesDeUsuario(UUID id, ConsultorioId consultorio, String passwordHash, int credencialesVersion) {

    @Override
    public String toString() {
        return "CredencialesDeUsuario[id=" + id + ", consultorio=" + consultorio + "]";
    }
}
