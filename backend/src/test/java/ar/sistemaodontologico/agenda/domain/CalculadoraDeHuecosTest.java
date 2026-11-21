package ar.sistemaodontologico.agenda.domain;

import static ar.sistemaodontologico.agenda.domain.Agenda.BUENOS_AIRES;
import static ar.sistemaodontologico.agenda.domain.Agenda.LUNES;
import static ar.sistemaodontologico.agenda.domain.Agenda.bloqueo;
import static ar.sistemaodontologico.agenda.domain.Agenda.cancelado;
import static ar.sistemaodontologico.agenda.domain.Agenda.lunesA;
import static ar.sistemaodontologico.agenda.domain.Agenda.sobreturno;
import static ar.sistemaodontologico.agenda.domain.Agenda.turno;
import static ar.sistemaodontologico.agenda.domain.ValidadoresTest.franja;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.sistemaodontologico.compartido.domain.error.ValidacionDeNegocio;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Cálculo de huecos (C-10, tareas 5.1 a 5.3; spec huecos-disponibles; design D7). */
class CalculadoraDeHuecosTest {

    // --- 5.1 sin límites ---

    @Test
    void mananaLibre() {
        assertThat(huecos(disponibilidad(franja("09:00", "10:00")), 30, 15, List.of(), List.of()))
                .containsExactly(lunesA("09:00"), lunesA("09:15"), lunesA("09:30"));
    }

    @Test
    void diaSinDisponibilidad() {
        DisponibilidadSemanal semana = disponibilidad(franja("09:00", "13:00"));

        assertThat(CalculadoraDeHuecos.calcular(semana, LUNES.minusDays(1), LUNES.minusDays(1), new Duracion(30), 15,
                List.of(), Optional.empty())).isEmpty();
    }

    @Test
    void duracionMayorQueLaFranja() {
        assertThat(huecos(disponibilidad(franja("09:00", "09:30")), 45, 15, List.of(), List.of())).isEmpty();
    }

    @Test
    void instantesEnUtc() {
        assertThat(huecos(disponibilidad(franja("09:00", "09:30")), 30, 30, List.of(), List.of()))
                .containsExactly(Instant.parse("2026-11-23T12:00:00Z"));
    }

    @Test
    void elPasoCorreDesdeElInicioDeLaFranja() {
        assertThat(huecos(disponibilidad(franja("09:10", "10:00")), 20, 20, List.of(), List.of()))
                .containsExactly(lunesA("09:10"), lunesA("09:30"));
    }

    @Test
    void lasFranjasContiguasSonUnaSola() {
        assertThat(huecos(disponibilidad(franja("09:00", "09:45"), franja("09:45", "10:30")), 60, 30, List.of(),
                List.of())).containsExactly(lunesA("09:00"), lunesA("09:30"));
    }

    @Test
    void turnoEnElMedio() {
        Turno confirmado = turno("09:30", 30);
        confirmado.confirmar();

        assertThat(huecos(disponibilidad(franja("09:00", "10:30")), 30, 30, List.of(confirmado), List.of()))
                .containsExactly(lunesA("09:00"), lunesA("10:00"));
    }

    @Test
    void elSobreturnoOcupaElHueco() {
        assertThat(huecos(disponibilidad(franja("09:00", "10:00")), 30, 30, List.of(sobreturno("09:00", 20)), List.of()))
                .containsExactly(lunesA("09:30"));
    }

    @Test
    void elCanceladoLiberaElHueco() {
        assertThat(huecos(disponibilidad(franja("09:00", "09:30")), 30, 30, List.of(cancelado("09:00", 30)), List.of()))
                .containsExactly(lunesA("09:00"));
    }

    @Test
    void bloqueoDeTodaLaTarde() {
        assertThat(huecos(disponibilidad(franja("15:00", "19:00")), 60, 60, List.of(),
                List.of(bloqueo("14:00", "18:00")))).containsExactly(lunesA("18:00"));
    }

    @Test
    void variasFechasEnOrdenCronologico() {
        DisponibilidadSemanal semana = new DisponibilidadSemanal(List.of(
                new FranjaSemanal(DayOfWeek.TUESDAY, LocalTime.parse("09:00"), LocalTime.parse("09:30")),
                new FranjaSemanal(DayOfWeek.MONDAY, LocalTime.parse("15:00"), LocalTime.parse("15:30")),
                new FranjaSemanal(DayOfWeek.MONDAY, LocalTime.parse("09:00"), LocalTime.parse("09:30"))), BUENOS_AIRES);

        assertThat(CalculadoraDeHuecos.calcular(semana, LUNES, LUNES.plusDays(1), new Duracion(30), 30, List.of(),
                Optional.empty())).containsExactly(lunesA("09:00"), lunesA("15:00"), lunesA("09:00").plusSeconds(86_400));
    }

    @Test
    void elPasoEsPositivoYMultiploDe5() {
        for (int invalido : new int[] {0, -5, 7}) {
            assertThatThrownBy(() -> huecos(disponibilidad(franja("09:00", "10:00")), 30, invalido, List.of(), List.of()))
                    .isInstanceOf(ValidacionDeNegocio.class);
        }
    }

    // --- 5.2 límites de reserva ---

    @Test
    void anticipacionDeDosHoras() {
        Clock ochoYMedia = Clock.fixed(lunesA("08:30"), BUENOS_AIRES);

        List<Instant> huecos = CalculadoraDeHuecos.calcular(disponibilidad(franja("09:00", "12:00")), LUNES, LUNES,
                new Duracion(30), 30, List.of(), Optional.of(new LimitesDeReserva(120, 60, Instant.now(ochoYMedia))));

        assertThat(huecos).first().isEqualTo(lunesA("10:30"));
    }

    @Test
    void masAllaDelHorizonte() {
        Instant ahora = lunesA("08:30");
        LocalDate en61Dias = LUNES.plusDays(61);
        DisponibilidadSemanal todaLaSemana = new DisponibilidadSemanal(
                java.util.Arrays.stream(DayOfWeek.values())
                        .map(dia -> new FranjaSemanal(dia, LocalTime.parse("09:00"), LocalTime.parse("13:00")))
                        .toList(), BUENOS_AIRES);

        assertThat(CalculadoraDeHuecos.calcular(todaLaSemana, en61Dias, en61Dias, new Duracion(30), 30, List.of(),
                Optional.of(new LimitesDeReserva(0, 60, ahora)))).isEmpty();
        // El día 60 todavía entra hasta las 08:30, la hora de "ahora": a esa hora ya no hay franja, así que queda vacío;
        // el 59 está completo.
        assertThat(CalculadoraDeHuecos.calcular(todaLaSemana, LUNES.plusDays(59), LUNES.plusDays(59), new Duracion(30),
                30, List.of(), Optional.of(new LimitesDeReserva(0, 60, ahora)))).hasSize(8);
    }

    @Test
    void sinLimitesParaElOdontologo() {
        assertThat(CalculadoraDeHuecos.calcular(disponibilidad(franja("09:00", "12:00")), LUNES, LUNES,
                new Duracion(30), 30, List.of(), Optional.empty())).first().isEqualTo(lunesA("09:00"));
    }

    // --- 5.3 horario de verano ---

    @Test
    void horarioDeVeranoSaltaLasHorasQueNoExisten() {
        // Argentina no tiene horario de verano. Madrid sí: el 29/3/2026 a las 02:00 los relojes pasan a las 03:00.
        // Las horas locales que no existen se saltean; no se corren a la hora siguiente, para no repetir huecos.
        ZoneId madrid = ZoneId.of("Europe/Madrid");
        LocalDate cambio = LocalDate.of(2026, 3, 29);
        DisponibilidadSemanal domingo = new DisponibilidadSemanal(
                List.of(new FranjaSemanal(DayOfWeek.SUNDAY, LocalTime.parse("01:00"), LocalTime.parse("04:00"))), madrid);

        List<Instant> huecos = CalculadoraDeHuecos.calcular(domingo, cambio, cambio, new Duracion(30), 30, List.of(),
                Optional.empty());

        assertThat(huecos).containsExactly(local(cambio, "01:00", madrid), local(cambio, "01:30", madrid),
                local(cambio, "03:00", madrid), local(cambio, "03:30", madrid));
    }

    private static Instant local(LocalDate fecha, String hora, ZoneId zona) {
        return ZonedDateTime.of(fecha, LocalTime.parse(hora), zona).toInstant();
    }

    private static List<Instant> huecos(DisponibilidadSemanal disponibilidad, int minutos, int paso, List<Turno> turnos,
            List<Bloqueo> bloqueos) {
        return CalculadoraDeHuecos.calcular(disponibilidad, LUNES, LUNES, new Duracion(minutos), paso,
                CalculadoraDeHuecos.ocupados(turnos, bloqueos), Optional.empty());
    }

    private static DisponibilidadSemanal disponibilidad(FranjaSemanal... franjas) {
        return ValidadoresTest.disponibilidad(franjas);
    }
}
