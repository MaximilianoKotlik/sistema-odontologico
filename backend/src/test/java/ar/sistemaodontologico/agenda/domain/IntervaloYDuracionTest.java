package ar.sistemaodontologico.agenda.domain;

import static ar.sistemaodontologico.agenda.domain.Agenda.lunes;
import static ar.sistemaodontologico.agenda.domain.Agenda.lunesA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.sistemaodontologico.compartido.domain.error.ValidacionDeNegocio;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** {@code Intervalo} y {@code Duracion} (C-10, tarea 3.1; design D3). */
class IntervaloYDuracionTest {

    @Test
    void elFinEsPosteriorAlInicio() {
        assertCodigo(() -> new Intervalo(lunesA("10:00"), lunesA("10:00")), "HORARIO_INVALIDO");
        assertCodigo(() -> new Intervalo(lunesA("10:00"), lunesA("09:00")), "HORARIO_INVALIDO");
    }

    @Test
    void semiabiertoLosContiguosNoSeSolapan() {
        Intervalo diez = lunes("10:00", "10:30");

        assertThat(diez.seSolapaCon(lunes("10:30", "11:00"))).isFalse();
        assertThat(diez.seSolapaCon(lunes("09:30", "10:00"))).isFalse();
        assertThat(diez.seSolapaCon(lunes("10:15", "10:45"))).isTrue();
        assertThat(diez.seSolapaCon(lunes("09:00", "12:00"))).isTrue();
        assertThat(diez.tocaA(lunes("10:30", "11:00"))).isTrue();
        assertThat(diez.tocaA(lunes("10:35", "11:00"))).isFalse();
    }

    @Test
    void contieneIncluyeLosBordes() {
        Intervalo manana = lunes("09:00", "13:00");

        assertThat(manana.contiene(lunes("09:00", "13:00"))).isTrue();
        assertThat(manana.contiene(lunes("12:30", "13:15"))).isFalse();
        assertThat(manana.contiene(lunes("08:55", "09:30"))).isFalse();
    }

    @Test
    void alineacionA5Minutos() {
        assertThat(lunes("10:00", "10:05").estaAlineadoA5Minutos()).isTrue();
        assertThat(lunes("10:02", "10:05").estaAlineadoA5Minutos()).isFalse();
        assertThat(new Intervalo(lunesA("10:00"), lunesA("10:05").plusMillis(600)).estaAlineadoA5Minutos()).isFalse();
    }

    @Test
    void duracionDe5A480EnMultiplosDe5() {
        assertThatCode(() -> new Duracion(5)).doesNotThrowAnyException();
        assertThatCode(() -> new Duracion(480)).doesNotThrowAnyException();
        for (int invalida : new int[] {0, 42, 485, -5}) {
            assertCodigo(() -> new Duracion(invalida), "DURACION_INVALIDA");
        }
        assertThat(new Duracion(45).comoDuration()).isEqualTo(Duration.ofMinutes(45));
    }

    static void assertCodigo(org.assertj.core.api.ThrowableAssert.ThrowingCallable accion, String codigo) {
        assertThatThrownBy(accion).isInstanceOfSatisfying(ValidacionDeNegocio.class,
                error -> assertThat(error.getCodigo()).isEqualTo(codigo));
    }
}
