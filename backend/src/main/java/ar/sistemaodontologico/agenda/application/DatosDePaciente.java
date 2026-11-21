package ar.sistemaodontologico.agenda.application;

/**
 * Lo que se escribe al cargar o reservar un turno para un paciente, tal como llega. El dominio lo normaliza y lo
 * valida ({@code Paciente}, {@code Dni}). Ningún dato aparece en {@link #toString()} (RN-GL-01).
 */
public record DatosDePaciente(String nombre, String apellido, String dni, String telefono, String email) {

    @Override
    public String toString() {
        return "DatosDePaciente[***]";
    }
}
