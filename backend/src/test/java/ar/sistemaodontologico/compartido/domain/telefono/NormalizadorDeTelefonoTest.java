package ar.sistemaodontologico.compartido.domain.telefono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.sistemaodontologico.compartido.domain.error.ErrorDeNegocio;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Spec telefono-e164. Solo números ficticios (regla dura 17). */
class NormalizadorDeTelefonoTest {

    // Primer test del ciclo (design D5): verifica con la librería real que el formato local con 15 y el
    // internacional con 9 normalizan igual. Si falla, se ajusta la spec antes de seguir.
    @Test
    void celularDeAmbaEnFormatoLocalConQuince() {
        assertThat(NormalizadorDeTelefono.normalizar("011 15-0000-0001")).isEqualTo("+5491100000001");
    }

    @Test
    void celularYaInternacionalConSeparadores() {
        assertThat(NormalizadorDeTelefono.normalizar("+54 9 11 0000-0001")).isEqualTo("+5491100000001");
    }

    @Test
    void mismoCelularEnDosFormatosDaElMismoValor() {
        assertThat(NormalizadorDeTelefono.normalizar("011 15 0000-0001"))
                .isEqualTo(NormalizadorDeTelefono.normalizar("+54 9 11 0000 0001"));
    }

    @Test
    void fijoSinElNueveDeCelular() {
        assertThat(NormalizadorDeTelefono.normalizar("011 0000-0001")).isEqualTo("+541100000001");
    }

    @ParameterizedTest
    @ValueSource(strings = {"123", "no tengo", "+54 9 11 0000-00001234567"})
    @NullAndEmptySource
    void rechazaTelefonosInvalidos(String texto) {
        assertThatThrownBy(() -> NormalizadorDeTelefono.normalizar(texto))
                .isInstanceOf(ErrorDeNegocio.class)
                .extracting("codigo").isEqualTo("TELEFONO_INVALIDO");
    }

    @Test
    void elErrorNoIncluyeElValorIngresado() {
        assertThatThrownBy(() -> NormalizadorDeTelefono.normalizar("12345"))
                .hasMessageNotContaining("12345");
    }
}
