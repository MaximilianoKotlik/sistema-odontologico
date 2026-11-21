package ar.sistemaodontologico.agenda.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Disponibilidad semanal del consultorio en su zona horaria (RN-AG-03, RN-TE-05). Las franjas del mismo día que se
 * tocan se fusionan en un horario continuo (design D5, aprobado en #18).
 */
public record DisponibilidadSemanal(List<FranjaSemanal> franjas, ZoneId zona) {

    public DisponibilidadSemanal {
        franjas = List.copyOf(Objects.requireNonNull(franjas, "franjas"));
        Objects.requireNonNull(zona, "zona");
    }

    /** Las franjas de ese día de la semana, ordenadas y con las contiguas fusionadas, en hora local. */
    public List<FranjaSemanal> franjasDe(DayOfWeek dia) {
        List<FranjaSemanal> delDia = franjas.stream()
                .filter(franja -> franja.dia() == dia)
                .sorted(Comparator.comparing(FranjaSemanal::inicio))
                .toList();
        List<FranjaSemanal> fusionadas = new ArrayList<>();
        for (FranjaSemanal franja : delDia) {
            int ultima = fusionadas.size() - 1;
            if (ultima >= 0 && !franja.inicio().isAfter(fusionadas.get(ultima).fin())) {
                FranjaSemanal anterior = fusionadas.get(ultima);
                fusionadas.set(ultima, new FranjaSemanal(dia, anterior.inicio(),
                        franja.fin().isAfter(anterior.fin()) ? franja.fin() : anterior.fin()));
            } else {
                fusionadas.add(franja);
            }
        }
        return fusionadas;
    }

    /** Las franjas de esa fecha local como instantes, ya fusionadas. */
    public List<Intervalo> intervalosDe(LocalDate fecha) {
        return franjasDe(fecha.getDayOfWeek()).stream()
                .map(franja -> new Intervalo(ZonedDateTime.of(fecha, franja.inicio(), zona).toInstant(),
                        ZonedDateTime.of(fecha, franja.fin(), zona).toInstant()))
                .toList();
    }

    /**
     * Si el intervalo cae completo dentro de una franja de su fecha local. Una franja no cruza la medianoche, así que
     * alcanza con mirar la fecha del inicio.
     */
    public boolean cubre(Intervalo intervalo) {
        LocalDate fecha = intervalo.inicio().atZone(zona).toLocalDate();
        return intervalosDe(fecha).stream().anyMatch(franja -> franja.contiene(intervalo));
    }
}
