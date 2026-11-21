package ar.sistemaodontologico.reserva.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Huecos ofrecibles al paciente, por fecha local del consultorio e instantes UTC (design D5). */
public record HuecosPublicos(String zonaHoraria, List<Dia> dias) {

    /** {@code atiende} distingue un día sin atención de un día que atiende pero no tiene huecos. */
    public record Dia(LocalDate fecha, boolean atiende, List<Instant> huecos) {
    }
}
