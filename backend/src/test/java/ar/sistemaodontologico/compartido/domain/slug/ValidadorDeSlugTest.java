package ar.sistemaodontologico.compartido.domain.slug;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.sistemaodontologico.compartido.domain.error.ErrorDeNegocio;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Spec slug-consultorio — Requirements: Formato del slug y Palabras reservadas. */
class ValidadorDeSlugTest {

    @ParameterizedTest
    @ValueSource(strings = {"consultorio-perez-2", "abc", "a1b", "demo"})
    void aceptaSlugsValidos(String slug) {
        assertThatCode(() -> ValidadorDeSlug.validar(slug)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ab", "-perez", "perez-", "perez--sur", "Perez", "pérez", "perez_sur", "perez sur", ""})
    void rechazaSlugsConFormatoInvalido(String slug) {
        assertThatThrownBy(() -> ValidadorDeSlug.validar(slug))
                .isInstanceOf(ErrorDeNegocio.class)
                .extracting("codigo").isEqualTo("SLUG_INVALIDO");
    }

    @org.junit.jupiter.api.Test
    void rechazaSlugDeCuarentaYUnCaracteres() {
        assertThatThrownBy(() -> ValidadorDeSlug.validar("a".repeat(41)))
                .extracting("codigo").isEqualTo("SLUG_INVALIDO");
        assertThatCode(() -> ValidadorDeSlug.validar("a".repeat(40))).doesNotThrowAnyException();
    }

    @org.junit.jupiter.api.Test
    void rechazaSlugNulo() {
        assertThatThrownBy(() -> ValidadorDeSlug.validar(null))
                .extracting("codigo").isEqualTo("SLUG_INVALIDO");
    }

    @ParameterizedTest
    @ValueSource(strings = {"app", "api", "login", "registro", "precios", "admin", "www", "health", "swagger"})
    void rechazaPalabrasReservadas(String slug) {
        assertThatThrownBy(() -> ValidadorDeSlug.validar(slug))
                .isInstanceOf(ErrorDeNegocio.class)
                .extracting("codigo").isEqualTo("SLUG_RESERVADO");
    }

    @ParameterizedTest
    @ValueSource(strings = {"c", "t", "APP", "Admin"})
    void lasReservadasSeComparanSinDistinguirMayusculas(String palabra) {
        org.assertj.core.api.Assertions.assertThat(PalabrasReservadas.contiene(palabra)).isTrue();
    }
}
