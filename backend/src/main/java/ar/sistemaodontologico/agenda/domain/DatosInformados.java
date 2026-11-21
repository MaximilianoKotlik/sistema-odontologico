package ar.sistemaodontologico.agenda.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Lo escrito en la reserva que marcó al paciente para revisión (DD-38): nombre, apellido, email opcional, cuándo y el
 * turno de origen, si lo hay. Son datos temporales: existen solo mientras el paciente tiene la revisión pendiente y se
 * borran al revisarlo (C-17). Ningún dato personal aparece en {@link #toString()} (RN-GL-01).
 */
public record DatosInformados(String nombre, String apellido, String email, Instant informadoAt, TurnoId turnoOrigen) {

    public DatosInformados {
        Objects.requireNonNull(informadoAt, "informadoAt");
        nombre = Textos.obligatorio(nombre, Paciente.LARGO_MAXIMO_NOMBRE);
        apellido = Textos.obligatorio(apellido, Paciente.LARGO_MAXIMO_NOMBRE);
        email = Paciente.normalizarEmail(email);
    }

    @Override
    public String toString() {
        return "DatosInformados[informadoAt=" + informadoAt + "]";
    }
}
