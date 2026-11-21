package ar.sistemaodontologico.agenda.domain;

import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

/** Fábricas de datos para los tests del dominio de agenda (C-10). Datos ficticios (regla dura 17). */
final class Agenda {

    static final ZoneId BUENOS_AIRES = ZoneId.of("America/Argentina/Buenos_Aires");
    /** Lunes. */
    static final LocalDate LUNES = LocalDate.of(2026, 11, 23);
    static final ConsultorioId CONSULTORIO = new ConsultorioId(UUID.randomUUID());
    static final PacienteId PACIENTE = new PacienteId(UUID.randomUUID());
    static final UUID PRESTACION = UUID.randomUUID();

    private Agenda() {
    }

    /** El lunes de prueba a esa hora local de Buenos Aires. */
    static Instant lunesA(String hora) {
        return en(LUNES, hora);
    }

    static Instant en(LocalDate fecha, String hora) {
        return ZonedDateTime.of(fecha, LocalTime.parse(hora), BUENOS_AIRES).toInstant();
    }

    static Intervalo lunes(String desde, String hasta) {
        return new Intervalo(lunesA(desde), lunesA(hasta));
    }

    static Turno turno(String inicio, int minutos) {
        return Turno.nuevo(nuevoId(), CONSULTORIO, PACIENTE, PRESTACION, lunesA(inicio), new Duracion(minutos),
                OrigenTurno.MANUAL);
    }

    static Turno sobreturno(String inicio, int minutos) {
        return Turno.nuevoSobreturno(nuevoId(), CONSULTORIO, PACIENTE, PRESTACION, lunesA(inicio), new Duracion(minutos));
    }

    static Turno cancelado(String inicio, int minutos) {
        Turno turno = turno(inicio, minutos);
        turno.cancelar(CanceladoPor.ODONTOLOGO, null);
        return turno;
    }

    static Bloqueo bloqueo(String desde, String hasta) {
        return new Bloqueo(new BloqueoId(UUID.randomUUID()), lunes(desde, hasta), null);
    }

    static TurnoId nuevoId() {
        return new TurnoId(UUID.randomUUID());
    }
}
