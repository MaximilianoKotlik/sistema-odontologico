package ar.sistemaodontologico.agenda.application;

import java.util.UUID;

/**
 * Un paciente del propio consultorio en el buscador del turno manual (C-12, design D7). Ningún dato aparece en
 * {@link #toString()} (RN-GL-01).
 */
public record PacienteEncontrado(UUID id, String nombre, String apellido, String dni, String telefono,
        boolean requiereRevision) {

    @Override
    public String toString() {
        return "PacienteEncontrado[id=" + id + "]";
    }
}
