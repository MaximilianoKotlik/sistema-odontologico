package ar.sistemaodontologico.compartido.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec aislamiento-tenant — Scenario: Identificadores ordenables y no secuenciales. */
class GeneradorDeIdsUuidV7Test {

    private final GeneradorDeIdsUuidV7 generador = new GeneradorDeIdsUuidV7();

    @Test
    void generaUuidVersionSiete() {
        assertThat(generador.nuevo().version()).isEqualTo(7);
    }

    @Test
    void dosIdsConsecutivosSonDistintosYOrdenados() {
        UUID primero = generador.nuevo();
        UUID segundo = generador.nuevo();

        assertThat(segundo).isNotEqualTo(primero);
        assertThat(segundo.toString()).isGreaterThan(primero.toString());
    }

    @Test
    void milIdsSonUnicosYCrecientes() {
        Set<UUID> vistos = new HashSet<>();
        String anterior = "";
        for (int i = 0; i < 1000; i++) {
            UUID id = generador.nuevo();
            assertThat(vistos.add(id)).isTrue();
            assertThat(id.toString()).isGreaterThan(anterior);
            anterior = id.toString();
        }
    }
}
