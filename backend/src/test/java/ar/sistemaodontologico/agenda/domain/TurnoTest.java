package ar.sistemaodontologico.agenda.domain;

import static ar.sistemaodontologico.agenda.domain.Agenda.CONSULTORIO;
import static ar.sistemaodontologico.agenda.domain.Agenda.PACIENTE;
import static ar.sistemaodontologico.agenda.domain.Agenda.PRESTACION;
import static ar.sistemaodontologico.agenda.domain.Agenda.lunesA;
import static ar.sistemaodontologico.agenda.domain.Agenda.sobreturno;
import static ar.sistemaodontologico.agenda.domain.Agenda.turno;
import static ar.sistemaodontologico.agenda.domain.IntervaloYDuracionTest.assertCodigo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.sistemaodontologico.compartido.domain.error.ConflictoDeNegocio;
import org.junit.jupiter.api.Test;

/** {@code Turno} (C-10, tarea 3.3; spec turnos). */
class TurnoTest {

    @Test
    void elFinSeCalcula() {
        assertThat(turno("10:00", 45).fin()).isEqualTo(lunesA("10:45"));
        assertThat(turno("10:00", 45).intervalo()).isEqualTo(new Intervalo(lunesA("10:00"), lunesA("10:45")));
    }

    @Test
    void unTurnoNuevoQuedaReservado() {
        Turno nuevo = turno("10:00", 30);

        assertThat(nuevo.estado()).isEqualTo(EstadoTurno.RESERVADO);
        assertThat(nuevo.esSobreturno()).isFalse();
        assertThat(nuevo.estaActivo()).isTrue();
    }

    @Test
    void reprogramarConservaLaDuracionYElId() {
        Turno limpieza = turno("10:00", 45);
        TurnoId id = limpieza.id();

        limpieza.reprogramar(lunesA("16:00"));

        assertThat(limpieza.id()).isEqualTo(id);
        assertThat(limpieza.inicio()).isEqualTo(lunesA("16:00"));
        assertThat(limpieza.fin()).isEqualTo(lunesA("16:45"));
    }

    @Test
    void ajustarLaDuracionRecalculaElFin() {
        Turno consulta = turno("10:00", 30);

        consulta.ajustarDuracion(new Duracion(50));

        assertThat(consulta.fin()).isEqualTo(lunesA("10:50"));
    }

    @Test
    void laDuracionEsUnaCopiaDeLaDeLaPrestacion() {
        // RN-AG-02: el turno guarda su Duracion; cambiar la prestación después no lo toca.
        Duracion deLaPrestacion = new Duracion(45);
        Turno limpieza = Turno.nuevo(Agenda.nuevoId(), CONSULTORIO, PACIENTE, PRESTACION, lunesA("10:00"),
                deLaPrestacion, OrigenTurno.LINK);

        assertThat(limpieza.duracion()).isEqualTo(new Duracion(45));
        assertThat(limpieza.tipoPrestacionId()).isEqualTo(PRESTACION);
    }

    @Test
    void inicioFueraDeLaGrilla() {
        assertCodigo(() -> turno("10:03", 30), "HORARIO_INVALIDO");
        Turno valido = turno("10:00", 30);
        assertCodigo(() -> valido.reprogramar(lunesA("16:02")), "HORARIO_INVALIDO");
        assertCodigo(() -> valido.reprogramar(lunesA("16:00").plusMillis(600)), "HORARIO_INVALIDO");
    }

    @Test
    void confirmarCancelarYAusente() {
        Turno turno = turno("10:00", 30);
        turno.confirmar();
        assertThat(turno.estado()).isEqualTo(EstadoTurno.CONFIRMADO);

        turno.cancelar(CanceladoPor.PACIENTE, "  Viaje  ");
        assertThat(turno.estado()).isEqualTo(EstadoTurno.CANCELADO);
        assertThat(turno.canceladoPor()).isEqualTo(CanceladoPor.PACIENTE);
        assertThat(turno.motivoCancelacion()).isEqualTo("Viaje");
        assertThat(turno.estaActivo()).isFalse();

        Turno otro = turno("10:00", 30);
        otro.marcarAusente(lunesA("10:10"));
        assertThat(otro.estado()).isEqualTo(EstadoTurno.AUSENTE);
    }

    @Test
    void unTurnoTerminalNoSeReprogramaNiSeAjusta() {
        Turno ausente = turno("10:00", 30);
        ausente.marcarAusente(lunesA("10:10"));

        assertThatThrownBy(() -> ausente.reprogramar(lunesA("16:00"))).isInstanceOf(ConflictoDeNegocio.class);
        assertThatThrownBy(() -> ausente.ajustarDuracion(new Duracion(60))).isInstanceOf(ConflictoDeNegocio.class);
        assertThat(ausente.inicio()).isEqualTo(lunesA("10:00"));
    }

    @Test
    void elSobreturnoSiempreEsManualYConservaLaMarca() {
        Turno sobreturno = sobreturno("10:00", 20);
        assertThat(sobreturno.origen()).isEqualTo(OrigenTurno.MANUAL);

        sobreturno.cancelar(CanceladoPor.ODONTOLOGO, null);

        assertThat(sobreturno.estado()).isEqualTo(EstadoTurno.CANCELADO);
        assertThat(sobreturno.esSobreturno()).isTrue();
    }

    @Test
    void unSobreturnoPorLinkEsInvalido() {
        assertCodigo(() -> Turno.restaurar(Agenda.nuevoId(), CONSULTORIO, PACIENTE, PRESTACION, lunesA("10:00"),
                new Duracion(20), EstadoTurno.RESERVADO, true, OrigenTurno.LINK, null, null, null), "VALIDACION");
    }

    @Test
    void restaurarValidaLaCoherenciaDeLaCancelacion() {
        assertCodigo(() -> Turno.restaurar(Agenda.nuevoId(), CONSULTORIO, PACIENTE, PRESTACION, lunesA("10:00"),
                new Duracion(20), EstadoTurno.CONFIRMADO, false, OrigenTurno.MANUAL, CanceladoPor.ODONTOLOGO, null, null),
                "VALIDACION");
        assertCodigo(() -> Turno.restaurar(Agenda.nuevoId(), CONSULTORIO, PACIENTE, PRESTACION, lunesA("10:00"),
                new Duracion(20), EstadoTurno.CANCELADO, false, OrigenTurno.MANUAL, null, null, null), "VALIDACION");
        Turno cancelado = Turno.restaurar(Agenda.nuevoId(), CONSULTORIO, PACIENTE, PRESTACION, lunesA("10:00"),
                new Duracion(20), EstadoTurno.CANCELADO, false, OrigenTurno.LINK, CanceladoPor.PACIENTE, "Viaje", "Nota");
        assertThat(cancelado.notaInterna()).isEqualTo("Nota");
    }

    @Test
    void notaDeHasta500YMotivoDeHasta200() {
        Turno turno = turno("10:00", 30);
        turno.editarNota("n".repeat(500));
        assertThat(turno.notaInterna()).hasSize(500);
        assertCodigo(() -> turno.editarNota("n".repeat(501)), "VALIDACION");
        turno.editarNota("   ");
        assertThat(turno.notaInterna()).isNull();

        assertCodigo(() -> turno.cancelar(CanceladoPor.ODONTOLOGO, "m".repeat(201)), "VALIDACION");
        assertThat(turno.estado()).isEqualTo(EstadoTurno.RESERVADO);
    }

    @Test
    void laRepresentacionTextualNoTieneLaNota() {
        Turno turno = turno("10:00", 30);
        turno.editarNota("Paciente con alergia a la penicilina");

        assertThat(turno.toString()).doesNotContain("penicilina").contains(turno.id().valor().toString());
    }

    @Test
    void dosTurnosConElMismoIdSonIguales() {
        Turno uno = turno("10:00", 30);
        Turno copia = Turno.restaurar(uno.id(), CONSULTORIO, PACIENTE, PRESTACION, lunesA("11:00"), new Duracion(45),
                EstadoTurno.CONFIRMADO, false, OrigenTurno.MANUAL, null, null, null);

        assertThat(uno).isEqualTo(copia).hasSameHashCodeAs(copia).isNotEqualTo(turno("10:00", 30));
    }
}
