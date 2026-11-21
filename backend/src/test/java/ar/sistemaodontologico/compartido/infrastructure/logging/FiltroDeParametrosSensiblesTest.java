package ar.sistemaodontologico.compartido.infrastructure.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

/** Cómo se enmascara la query (C-12, tarea 4.2): solo los parámetros sensibles, el resto queda igual. */
class FiltroDeParametrosSensiblesTest {

    @Test
    void soloElParametroSensible() {
        assertThat(FiltroDeParametrosSensibles.enmascarar("a=1&q=90000001&b=2", Set.of("q")))
                .isEqualTo("a=1&q=***&b=2");
    }

    @Test
    void repetidoSinValorOVacio() {
        assertThat(FiltroDeParametrosSensibles.enmascarar("q=Ana&q=Prueba", Set.of("q"))).isEqualTo("q=***&q=***");
        assertThat(FiltroDeParametrosSensibles.enmascarar("q", Set.of("q"))).isEqualTo("q=***");
        assertThat(FiltroDeParametrosSensibles.enmascarar("q=", Set.of("q"))).isEqualTo("q=***");
    }

    @Test
    void unNombreQueSoloEmpiezaIgualNoSeToca() {
        assertThat(FiltroDeParametrosSensibles.enmascarar("qq=1&xq=2", Set.of("q"))).isEqualTo("qq=1&xq=2");
    }
}
