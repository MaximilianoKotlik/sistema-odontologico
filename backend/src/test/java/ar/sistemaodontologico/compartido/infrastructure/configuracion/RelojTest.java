package ar.sistemaodontologico.compartido.infrastructure.configuracion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** Design D2: un único reloj del sistema, en UTC. */
class RelojTest {

    @Test
    void elRelojDelSistemaEstaEnUtc() {
        assertThat(new Reloj().clock().getZone()).isEqualTo(ZoneOffset.UTC);
    }
}
