package ar.sistemaodontologico.agenda.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Un paciente en la lista y en el detalle de C-17 (spec revision-pacientes). {@code informado} es {@code null} si no
 * tiene revisión pendiente (DD-38). Ningún dato personal aparece en {@link #toString()} (RN-GL-01).
 */
public record PacienteDeLaLista(UUID id, String nombre, String apellido, String dni, String telefono, String email,
        boolean requiereRevision, LoInformado informado) {

    /** Lo escrito en la reserva que disparó la marca; {@code turno} es {@code null} si ya no existe. */
    public record LoInformado(String nombre, String apellido, String email, Instant informadoAt,
            TurnoDeOrigen turno) {

        @Override
        public String toString() {
            return "LoInformado[informadoAt=" + informadoAt + "]";
        }
    }

    public record TurnoDeOrigen(UUID id, Instant inicio, String prestacion) {
    }

    @Override
    public String toString() {
        return "PacienteDeLaLista[id=" + id + "]";
    }
}
