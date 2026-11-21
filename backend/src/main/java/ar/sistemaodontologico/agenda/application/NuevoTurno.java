package ar.sistemaodontologico.agenda.application;

import ar.sistemaodontologico.agenda.domain.PacienteId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Pedido de un turno nuevo (design D4 y D5). La duración es la de la prestación salvo que el odontólogo pida otra;
 * el paciente no la elige ni pide sobreturnos.
 */
public record NuevoTurno(PacienteId paciente, UUID prestacion, Instant inicio, Optional<Integer> duracionMin,
        boolean sobreturno) {

    public NuevoTurno {
        Objects.requireNonNull(paciente, "paciente");
        Objects.requireNonNull(prestacion, "prestacion");
        Objects.requireNonNull(inicio, "inicio");
        Objects.requireNonNull(duracionMin, "duracionMin");
    }
}
