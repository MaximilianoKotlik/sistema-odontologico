package ar.sistemaodontologico.agenda.application;

import ar.sistemaodontologico.agenda.domain.Turno;
import java.util.Objects;

/**
 * Un turno leído de la base con su {@code version}, que el cliente devuelve en cada cambio para detectar ediciones
 * concurrentes (RN-GL-02, design D2 y D7).
 */
public record TurnoGuardado(Turno turno, long version) {

    public TurnoGuardado {
        Objects.requireNonNull(turno, "turno");
    }
}
