package ar.sistemaodontologico.compartido.domain.slug;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Spec slug-consultorio — Requirement: Sugerencia de slug a partir del nombre. */
class SugeridorDeSlugTest {

    @Test
    void quitaTildesEspaciosDoblesYSimbolos() {
        assertThat(SugeridorDeSlug.sugerir("Consultorio Odontológico  Peña & Asociados"))
                .isEqualTo("consultorio-odontologico-pena-asociados");
    }

    @ParameterizedTest
    @CsvSource({
        "Admin, admin-consultorio",
        "Té, te-consultorio",
        "  Dra. Gómez  , dra-gomez",
        "Consultorio Nº 5, consultorio-n-5",
        "Ünïcö, unico",
        "!!!, consultorio"
    })
    void sugiereUnSlugValido(String nombre, String esperado) {
        assertThat(SugeridorDeSlug.sugerir(nombre)).isEqualTo(esperado);
    }

    @Test
    void recortaACuarentaCaracteresSinGuionFinal() {
        String sugerido = SugeridorDeSlug.sugerir("Centro Integral de Odontología Avanzada del Sur Bonaerense");

        assertThat(sugerido).hasSizeLessThanOrEqualTo(40).doesNotEndWith("-");
        assertThat(sugerido).isEqualTo("centro-integral-de-odontologia-avanzada");
    }

    @Test
    void elResultadoSiempreEsValido() {
        for (String nombre : new String[] {"Admin", "Té", "x", "API", "---", "Ñandú Dental", "a".repeat(80)}) {
            String sugerido = SugeridorDeSlug.sugerir(nombre);
            assertThatCode(() -> ValidadorDeSlug.validar(sugerido)).as(nombre).doesNotThrowAnyException();
        }
    }
}
