package ar.sistemaodontologico.agenda.domain;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Franja de atención de un día de la semana, en hora local del consultorio. Es el value object propio de agenda
 * (design D2): C-11 y C-15 lo arman desde la grilla de C-08 en {@code application}.
 */
public record FranjaSemanal(DayOfWeek dia, LocalTime inicio, LocalTime fin) {

    public FranjaSemanal {
        Objects.requireNonNull(dia, "dia");
        Objects.requireNonNull(inicio, "inicio");
        Objects.requireNonNull(fin, "fin");
        if (!fin.isAfter(inicio)) {
            throw new IllegalArgumentException("El fin de la franja tiene que ser posterior al inicio");
        }
    }
}
