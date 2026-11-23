package ar.sistemaodontologico.agenda.application;

/**
 * Lo que el odontólogo corrige al revisar un paciente (C-17): el DNI y el teléfono no se corrigen en v1. Ningún dato
 * aparece en {@link #toString()} (RN-GL-01).
 */
public record DatosCorregidos(String nombre, String apellido, String email) {

    @Override
    public String toString() {
        return "DatosCorregidos[]";
    }
}
