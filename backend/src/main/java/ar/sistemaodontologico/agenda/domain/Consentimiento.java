package ar.sistemaodontologico.agenda.domain;

import java.time.Instant;
import java.util.Objects;

/** Aceptación de la política de privacidad de quien reserva por el link: cuándo y qué versión (LG-03). */
public record Consentimiento(Instant aceptadoAt, String version) {

    public static final int LARGO_MAXIMO_VERSION = 40;

    public Consentimiento {
        Objects.requireNonNull(aceptadoAt, "aceptadoAt");
        version = Textos.obligatorio(version, LARGO_MAXIMO_VERSION);
    }
}
