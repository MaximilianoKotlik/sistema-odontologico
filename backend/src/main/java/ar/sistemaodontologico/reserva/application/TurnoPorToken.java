package ar.sistemaodontologico.reserva.application;

import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import java.time.Instant;
import java.util.UUID;

/**
 * El turno al que apunta un token de gestión, con lo justo para la vista y las modificaciones (design D3 y D8). No
 * trae la nota, el motivo de cancelación, el DNI, el teléfono ni el email. El nombre no aparece en {@link #toString()}.
 */
public record TurnoPorToken(ConsultorioId consultorio, UUID turno, UUID paciente, UUID prestacionId, String prestacion,
        Instant inicio, Instant fin, int duracionMin, String estado, String canceladoPor, long version, String nombre,
        String apellido) {

    @Override
    public String toString() {
        return "TurnoPorToken[turno=" + turno + ", estado=" + estado + "]";
    }
}
