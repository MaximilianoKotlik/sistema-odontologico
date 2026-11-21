package ar.sistemaodontologico.consultorio.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.sistemaodontologico.compartido.domain.error.ValidacionDeNegocio;
import org.junit.jupiter.api.Test;

/** Reglas con las que se toman reservas (C-08, tarea 3.1; spec configuracion-consultorio, design D9). */
class ReglasDeReservaTest {

    @Test
    void losValoresPorDefectoSonValidos() {
        assertThat(ReglasDeReserva.POR_DEFECTO)
                .isEqualTo(new ReglasDeReserva(1440, 120, 60, 15));
    }

    @Test
    void anticipacionDeCancelacionDe0A10080() {
        assertThatCode(() -> new ReglasDeReserva(0, 0, 1, 15)).doesNotThrowAnyException();
        assertThatCode(() -> new ReglasDeReserva(10080, 0, 1, 15)).doesNotThrowAnyException();
        assertValidacion(() -> new ReglasDeReserva(-1, 0, 1, 15), "VALIDACION");
        assertValidacion(() -> new ReglasDeReserva(10081, 0, 1, 15), "VALIDACION");
    }

    @Test
    void anticipacionDeReservaNoNegativa() {
        assertValidacion(() -> new ReglasDeReserva(1440, -1, 60, 15), "VALIDACION");
    }

    @Test
    void horizonteDe1A365Dias() {
        assertThatCode(() -> new ReglasDeReserva(1440, 0, 365, 15)).doesNotThrowAnyException();
        assertValidacion(() -> new ReglasDeReserva(1440, 0, 0, 15), "VALIDACION");
        assertValidacion(() -> new ReglasDeReserva(1440, 0, 366, 15), "VALIDACION");
    }

    @Test
    void pasoDeHuecosPermitido() {
        for (int paso : new int[] {5, 10, 15, 20, 30}) {
            assertThatCode(() -> new ReglasDeReserva(1440, 120, 60, paso)).doesNotThrowAnyException();
        }
        assertValidacion(() -> new ReglasDeReserva(1440, 120, 60, 25), "VALIDACION");
    }

    @Test
    void laAnticipacionDeReservaEsMenorQueElHorizonte() {
        // Pedido de Maxi en #15: con anticipación >= horizonte no quedaría ningún hueco reservable.
        assertThatCode(() -> new ReglasDeReserva(1440, 1439, 1, 15)).doesNotThrowAnyException();
        assertValidacion(() -> new ReglasDeReserva(1440, 1440, 1, 15), "ANTICIPACION_FUERA_DE_HORIZONTE");
        assertThatThrownBy(() -> new ReglasDeReserva(1440, 2000, 1, 15))
                .isInstanceOfSatisfying(ValidacionDeNegocio.class, error -> assertThat(error.getPropiedades())
                        .containsEntry("campo", "anticipacionReservaMin"));
    }

    private static void assertValidacion(org.assertj.core.api.ThrowableAssert.ThrowingCallable accion, String codigo) {
        assertThatThrownBy(accion).isInstanceOfSatisfying(ValidacionDeNegocio.class,
                error -> assertThat(error.getCodigo()).isEqualTo(codigo));
    }
}
