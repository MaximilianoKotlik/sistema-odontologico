package ar.sistemaodontologico.auth.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.sistemaodontologico.compartido.domain.error.ErrorDeNegocio;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

/** Spec registro-odontologo — Requirement: Política de contraseña (RN-AU-01). */
class PoliticaDeContrasenaTest {

    private static final String EMAIL = "a.perez@example.com";

    @Test
    void nueveCaracteresEsDebil() {
        assertThatThrownBy(() -> PoliticaDeContrasena.validar("123456789", EMAIL))
                .isInstanceOf(ErrorDeNegocio.class)
                .extracting("codigo").isEqualTo("PASSWORD_DEBIL");
    }

    @Test
    void diezCaracteresAlcanzan() {
        assertThatCode(() -> PoliticaDeContrasena.validar("1234567890", EMAIL)).doesNotThrowAnyException();
    }

    @Test
    void igualAlEmailEsDebilAunqueCambienLasMayusculas() {
        assertThatThrownBy(() -> PoliticaDeContrasena.validar("A.Perez@Example.com", EMAIL))
                .extracting("codigo").isEqualTo("PASSWORD_DEBIL");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void vaciaEsDebil(String contrasena) {
        assertThatThrownBy(() -> PoliticaDeContrasena.validar(contrasena, EMAIL))
                .extracting("codigo").isEqualTo("PASSWORD_DEBIL");
    }

    @Test
    void elErrorNoIncluyeLaContrasena() {
        assertThatThrownBy(() -> PoliticaDeContrasena.validar("corta-xyz", EMAIL))
                .hasMessageNotContaining("corta-xyz");
    }
}
