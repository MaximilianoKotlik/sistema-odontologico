package ar.sistemaodontologico.agenda.domain;

import static ar.sistemaodontologico.agenda.domain.Agenda.LUNES;
import static ar.sistemaodontologico.agenda.domain.Agenda.en;
import static ar.sistemaodontologico.agenda.domain.Agenda.lunes;
import static ar.sistemaodontologico.agenda.domain.Agenda.lunesA;
import static ar.sistemaodontologico.agenda.domain.IntervaloYDuracionTest.assertCodigo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** {@code Bloqueo} (C-10, tarea 3.4; spec bloqueos, Requirement: Bloqueo con inicio y fin explícitos). */
class BloqueoTest {

    private static final BloqueoId ID = new BloqueoId(UUID.randomUUID());

    @Test
    void bloqueoDeUnaTarde() {
        Bloqueo curso = new Bloqueo(ID, lunes("14:00", "18:00"), "  Curso ");

        assertThat(curso.motivo()).isEqualTo("Curso");
    }

    @Test
    void bloqueoDeVariosDias() {
        assertThatCode(() -> new Bloqueo(ID, new Intervalo(en(LUNES, "00:00"), en(LUNES.plusDays(5), "00:00")), null))
                .doesNotThrowAnyException();
    }

    @Test
    void finNoPosteriorAlInicio() {
        assertCodigo(() -> new Bloqueo(ID, new Intervalo(lunesA("18:00"), lunesA("14:00")), null), "HORARIO_INVALIDO");
    }

    @Test
    void fueraDeLaGrillaDe5Minutos() {
        assertCodigo(() -> new Bloqueo(ID, lunes("14:02", "18:00"), null), "HORARIO_INVALIDO");
    }

    @Test
    void motivoDeHasta120Caracteres() {
        assertThatCode(() -> new Bloqueo(ID, lunes("14:00", "18:00"), "m".repeat(120))).doesNotThrowAnyException();
        assertCodigo(() -> new Bloqueo(ID, lunes("14:00", "18:00"), "m".repeat(121)), "VALIDACION");
        assertThat(new Bloqueo(ID, lunes("14:00", "18:00"), " ").motivo()).isNull();
    }
}
