package ar.sistemaodontologico.agenda.domain;

import static ar.sistemaodontologico.agenda.domain.Agenda.BUENOS_AIRES;
import static ar.sistemaodontologico.agenda.domain.Agenda.LUNES;
import static ar.sistemaodontologico.agenda.domain.Agenda.bloqueo;
import static ar.sistemaodontologico.agenda.domain.Agenda.cancelado;
import static ar.sistemaodontologico.agenda.domain.Agenda.lunes;
import static ar.sistemaodontologico.agenda.domain.Agenda.lunesA;
import static ar.sistemaodontologico.agenda.domain.Agenda.sobreturno;
import static ar.sistemaodontologico.agenda.domain.Agenda.turno;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.sistemaodontologico.compartido.domain.error.ConflictoDeNegocio;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Disponibilidad, solapamiento y bloqueos (C-10, tareas 4.1 a 4.3; specs turnos y bloqueos; design D4 y D5). */
class ValidadoresTest {

    private static final DisponibilidadSemanal LUNES_MANANA = disponibilidad(franja("09:00", "13:00"));

    @Nested
    class Disponibilidad {

        @Test
        void materializaLasFranjasDeUnaFechaEnLaZonaDelConsultorio() {
            assertThat(LUNES_MANANA.intervalosDe(LUNES)).containsExactly(lunes("09:00", "13:00"));
            assertThat(LUNES_MANANA.intervalosDe(LUNES).get(0).inicio()).isEqualTo(Instant.parse("2026-11-23T12:00:00Z"));
            assertThat(LUNES_MANANA.intervalosDe(LUNES.plusDays(1))).isEmpty();
        }

        @Test
        void fusionaLasFranjasContiguas() {
            DisponibilidadSemanal partida = disponibilidad(franja("13:00", "17:00"), franja("09:00", "13:00"),
                    franja("18:00", "19:00"));

            assertThat(partida.intervalosDe(LUNES)).containsExactly(lunes("09:00", "17:00"), lunes("18:00", "19:00"));
        }

        @Test
        void cubreUnTurnoQueEntraCompleto() {
            assertThat(LUNES_MANANA.cubre(lunes("09:00", "09:30"))).isTrue();
            assertThat(LUNES_MANANA.cubre(lunes("12:30", "13:15"))).isFalse();
            assertThat(LUNES_MANANA.cubre(new Intervalo(lunesA("09:00").plusSeconds(86_400),
                    lunesA("09:30").plusSeconds(86_400)))).isFalse();
        }
    }

    @Nested
    class Solapamiento {

        @Test
        void turnoSolapadoConLosTurnosEnConflicto() {
            Turno diez = turno("10:00", 30);

            assertThatThrownBy(() -> validar(turno("10:15", 30), List.of(diez, turno("11:00", 30)), List.of()))
                    .isInstanceOfSatisfying(ConflictoDeNegocio.class, error -> {
                        assertThat(error.getCodigo()).isEqualTo("TURNO_SOLAPADO");
                        assertThat(error.getPropiedades())
                                .containsEntry("turnosEnConflicto", List.of(diez.id().valor().toString()));
                    });
        }

        @Test
        void contiguosCanceladosYAusentesNoCuentan() {
            Turno ausente = turno("10:30", 30);
            ausente.marcarAusente(lunesA("11:00"));

            assertThatCode(() -> validar(turno("10:30", 30), List.of(turno("10:00", 30), cancelado("10:30", 30), ausente),
                    List.of())).doesNotThrowAnyException();
        }

        @Test
        void unSobreturnoActivoNoBloqueaAOtroTurno() {
            assertThatCode(() -> validar(turno("10:00", 30), List.of(sobreturno("10:00", 20)), List.of()))
                    .doesNotThrowAnyException();
        }

        @Test
        void elSobreturnoSoloSeValidaContraBloqueos() {
            assertThatCode(() -> validar(sobreturno("10:00", 20), List.of(turno("10:00", 30)), List.of()))
                    .doesNotThrowAnyException();
            assertThatCode(() -> validar(sobreturno("13:00", 20), List.of(), List.of())).doesNotThrowAnyException();
            assertCodigo(() -> validar(sobreturno("11:30", 20), List.of(), List.of(bloqueo("11:00", "12:00"))),
                    "HORARIO_BLOQUEADO");
        }

        @Test
        void unTurnoNoChocaConsigoMismoAlReprogramarse() {
            Turno turno = turno("10:00", 30);
            List<Turno> agenda = List.of(turno);
            turno.reprogramar(lunesA("10:15"));

            assertThatCode(() -> validar(turno, agenda, List.of())).doesNotThrowAnyException();
        }

        @Test
        void fueraDeLaDisponibilidad() {
            assertCodigo(() -> validar(turno("12:30", 45), List.of(), List.of()), "FUERA_DE_DISPONIBILIDAD");
        }

        @Test
        void sobreFranjasContiguas() {
            DisponibilidadSemanal partida = disponibilidad(franja("09:00", "13:00"), franja("13:00", "17:00"));

            assertThatCode(() -> ValidadorDeSolapamiento.validar(turno("12:30", 60), List.of(), List.of(), partida))
                    .doesNotThrowAnyException();
        }

        @Test
        void enLaZonaDelConsultorio() {
            // Las 12:00 UTC del lunes son las 09:00 en Buenos Aires.
            Turno nueveLocal = Turno.nuevo(Agenda.nuevoId(), Agenda.CONSULTORIO, Agenda.PACIENTE, Agenda.PRESTACION,
                    Instant.parse("2026-11-23T12:00:00Z"), new Duracion(30), OrigenTurno.LINK);

            assertThatCode(() -> validar(nueveLocal, List.of(), List.of())).doesNotThrowAnyException();
        }

        @Test
        void elOrdenEsBloqueoDisponibilidadSolapamiento() {
            // Un turno con los tres problemas a la vez da siempre el mismo error: primero el bloqueo.
            Turno conTodo = turno("12:30", 45);
            List<Turno> ocupado = List.of(turno("12:00", 60));

            assertCodigo(() -> validar(conTodo, ocupado, List.of(bloqueo("12:45", "13:30"))), "HORARIO_BLOQUEADO");
            assertCodigo(() -> validar(conTodo, ocupado, List.of()), "FUERA_DE_DISPONIBILIDAD");
            assertCodigo(() -> validar(turno("12:15", 30), ocupado, List.of()), "TURNO_SOLAPADO");
        }

        @Test
        void unBloqueoContiguoNoCuenta() {
            assertThatCode(() -> validar(turno("10:00", 30), List.of(), List.of(bloqueo("10:30", "11:00"))))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    class Bloqueos {

        @Test
        void bloqueoSobreTodosLosTurnosActivosIncluidosSobreturnos() {
            Turno confirmado = turno("10:00", 30);
            confirmado.confirmar();
            Turno sobreturno = sobreturno("11:00", 20);

            assertThatThrownBy(() -> ValidadorDeBloqueo.validar(bloqueo("09:00", "13:00"),
                    List.of(confirmado, sobreturno, turno("13:00", 30))))
                    .isInstanceOfSatisfying(ConflictoDeNegocio.class, error -> {
                        assertThat(error.getCodigo()).isEqualTo("BLOQUEO_CON_TURNOS");
                        assertThat(error.getPropiedades()).containsEntry("turnosEnConflicto",
                                List.of(confirmado.id().valor().toString(), sobreturno.id().valor().toString()));
                    });
        }

        @Test
        void canceladosYContiguosNoImpidenElBloqueo() {
            assertThatCode(() -> ValidadorDeBloqueo.validar(bloqueo("10:30", "13:00"),
                    List.of(cancelado("11:00", 30), turno("10:00", 30)))).doesNotThrowAnyException();
        }

        @Test
        void bloqueosSuperpuestosSonValidos() {
            // D10: el validador de bloqueos no mira otros bloqueos.
            assertThatCode(() -> ValidadorDeBloqueo.validar(bloqueo("10:00", "11:00"), List.of()))
                    .doesNotThrowAnyException();
        }
    }

    private static void validar(Turno candidato, List<Turno> turnos, List<Bloqueo> bloqueos) {
        ValidadorDeSolapamiento.validar(candidato, turnos, bloqueos, LUNES_MANANA);
    }

    static DisponibilidadSemanal disponibilidad(FranjaSemanal... franjas) {
        return new DisponibilidadSemanal(List.of(franjas), BUENOS_AIRES);
    }

    static FranjaSemanal franja(String desde, String hasta) {
        return new FranjaSemanal(DayOfWeek.MONDAY, LocalTime.parse(desde), LocalTime.parse(hasta));
    }

    private static void assertCodigo(org.assertj.core.api.ThrowableAssert.ThrowingCallable accion, String codigo) {
        assertThatThrownBy(accion).isInstanceOfSatisfying(ConflictoDeNegocio.class,
                error -> assertThat(error.getCodigo()).isEqualTo(codigo));
    }
}
