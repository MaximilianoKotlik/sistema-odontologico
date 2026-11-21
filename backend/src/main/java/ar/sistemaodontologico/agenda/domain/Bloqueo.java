package ar.sistemaodontologico.agenda.domain;

import ar.sistemaodontologico.compartido.domain.error.ValidacionDeNegocio;
import java.util.Objects;

/**
 * Período en que el consultorio no atiende (spec bloqueos): inicio y fin explícitos en la grilla de 5 minutos y un
 * motivo opcional de hasta 120 caracteres. Puede abarcar varios días y superponerse con otros bloqueos (design D10).
 */
public record Bloqueo(BloqueoId id, Intervalo intervalo, String motivo) {

    public static final int LARGO_MAXIMO_MOTIVO = 120;

    public Bloqueo {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(intervalo, "intervalo");
        if (!intervalo.estaAlineadoA5Minutos()) {
            throw new ValidacionDeNegocio(Intervalo.CODIGO_HORARIO_INVALIDO,
                    "El bloqueo tiene que empezar y terminar en múltiplos de 5 minutos");
        }
        motivo = Textos.opcional(motivo, LARGO_MAXIMO_MOTIVO);
    }
}
