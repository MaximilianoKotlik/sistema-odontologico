package ar.sistemaodontologico.agenda.domain;

import ar.sistemaodontologico.compartido.domain.error.ValidacionDeNegocio;
import java.time.Duration;

/** Duración de un turno: de 5 a 480 minutos, en múltiplos de 5 (RN-AG-08, el mismo rango que tipo_prestacion). */
public record Duracion(int minutos) {

    public static final String CODIGO_INVALIDA = "DURACION_INVALIDA";

    public Duracion {
        if (minutos < 5 || minutos > 480 || minutos % 5 != 0) {
            throw new ValidacionDeNegocio(CODIGO_INVALIDA,
                    "La duración tiene que ser de 5 a 480 minutos, en múltiplos de 5");
        }
    }

    public Duration comoDuration() {
        return Duration.ofMinutes(minutos);
    }
}
