package ar.sistemaodontologico.auth.domain;

import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Odontólogo recién registrado. Solo lleva el hash de la contraseña, nunca la contraseña. */
public record NuevoUsuario(
        UUID id,
        ConsultorioId consultorio,
        String email,
        String passwordHash,
        String nombre,
        String apellido,
        Instant registradoAt,
        String terminosVersion) {

    public NuevoUsuario {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(consultorio, "consultorio");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(passwordHash, "passwordHash");
        Objects.requireNonNull(registradoAt, "registradoAt");
        Objects.requireNonNull(terminosVersion, "terminosVersion");
    }

    @Override
    public String toString() {
        // Sin email ni hash: el registro no debe aparecer con datos personales en un log.
        return "NuevoUsuario[id=" + id + ", consultorio=" + consultorio + "]";
    }
}
