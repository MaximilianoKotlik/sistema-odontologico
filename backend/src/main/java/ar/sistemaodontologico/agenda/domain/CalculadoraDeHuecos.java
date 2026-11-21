package ar.sistemaodontologico.agenda.domain;

import ar.sistemaodontologico.compartido.domain.error.ValidacionDeNegocio;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Inicios en los que cabe un turno de cierta duración (spec huecos-disponibles, design D7). Para cada fecha local del
 * rango toma las franjas fusionadas de su día, avanza de a {@code paso} desde el inicio de cada franja mientras el
 * turno entre completo, convierte a instantes en la zona del consultorio y descarta los que pisan algo ocupado o quedan
 * fuera de los límites. El resultado está en orden cronológico.
 *
 * <p>Horario de verano (hoy Argentina no tiene): una hora local que no existe se saltea, en vez de correrla a la
 * siguiente, para no repetir huecos; en una hora repetida se usa el primer offset ({@code ZonedDateTime.of}).
 */
public final class CalculadoraDeHuecos {

    private CalculadoraDeHuecos() {
    }

    /**
     * <p><strong>Qué ocupa según quién pide</strong> (revisión de Maxi en #18, lo decide C-12): para la reserva pública
     * los sobreturnos ocupan (RN-RP-01) y se pasa {@link #ocupados}. El odontólogo, en cambio, puede crear un turno
     * normal encima de un sobreturno ({@code ValidadorDeSolapamiento} los ignora), así que para él hay que pasar los
     * ocupados sin los sobreturnos: si no, el diálogo de reprogramar de C-14 escondería horarios que sí se pueden usar.
     *
     * <p><strong>Huecos en el pasado</strong>: sin límites, se devuelven también los inicios de hoy que ya pasaron.
     * Para el odontólogo conviene pasar límites con anticipación 0 en vez de {@code Optional.empty()}.
     *
     * @param paso minutos entre inicios, positivo y múltiplo de 5 (RN-AG-08); los valores que admite cada consultorio
     *     (5 a 30) los valida su configuración en C-08
     * @param ocupados bloqueos y turnos activos; con o sin sobreturnos según quién pide (ver arriba)
     * @param limites los de la reserva pública; para el odontólogo, anticipación 0 para no ofrecer el pasado
     */
    public static List<Instant> calcular(DisponibilidadSemanal disponibilidad, LocalDate desde, LocalDate hasta,
            Duracion duracion, int paso, Collection<Intervalo> ocupados, Optional<LimitesDeReserva> limites) {
        if (paso <= 0 || paso % 5 != 0) {
            throw new ValidacionDeNegocio("VALIDACION", "El paso de huecos no es válido");
        }
        List<Instant> huecos = new ArrayList<>();
        for (LocalDate fecha = desde; !fecha.isAfter(hasta); fecha = fecha.plusDays(1)) {
            for (FranjaSemanal franja : disponibilidad.franjasDe(fecha.getDayOfWeek())) {
                agregarHuecos(huecos, disponibilidad, fecha, franja, duracion, paso, ocupados, limites);
            }
        }
        return huecos;
    }

    /** Lo que ocupa horario para los huecos: los bloqueos y los turnos activos, sobreturnos incluidos (RN-RP-01). */
    public static List<Intervalo> ocupados(Collection<Turno> turnos, Collection<Bloqueo> bloqueos) {
        return Stream.concat(
                        turnos.stream().filter(Turno::estaActivo).map(Turno::intervalo),
                        bloqueos.stream().map(Bloqueo::intervalo))
                .toList();
    }

    private static void agregarHuecos(List<Instant> huecos, DisponibilidadSemanal disponibilidad, LocalDate fecha,
            FranjaSemanal franja, Duracion duracion, int paso, Collection<Intervalo> ocupados,
            Optional<LimitesDeReserva> limites) {
        int finDeLaFranja = minutosDelDia(franja.fin());
        for (int minuto = minutosDelDia(franja.inicio()); minuto + duracion.minutos() <= finDeLaFranja;
                minuto += paso) {
            LocalDateTime local = LocalDateTime.of(fecha, LocalTime.of(minuto / 60, minuto % 60));
            if (disponibilidad.zona().getRules().getValidOffsets(local).isEmpty()) {
                continue;
            }
            Instant inicio = ZonedDateTime.of(local, disponibilidad.zona()).toInstant();
            Intervalo candidato = new Intervalo(inicio, inicio.plus(duracion.comoDuration()));
            boolean libre = ocupados.stream().noneMatch(ocupado -> ocupado.seSolapaCon(candidato));
            if (libre && limites.map(limite -> limite.admite(inicio)).orElse(true)) {
                huecos.add(inicio);
            }
        }
    }

    private static int minutosDelDia(LocalTime hora) {
        return hora.getHour() * 60 + hora.getMinute();
    }
}
