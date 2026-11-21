package ar.sistemaodontologico.reserva.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Plazo para cancelar o reprogramar (C-15, tarea 3.2, RN-RP-04). */
class PlazoDeModificacionTest {

    private static final Instant INICIO = Instant.parse("2026-12-07T13:00:00Z");
    private static final int UN_DIA = 1440;

    @Test
    void dentroDelPlazo() {
        assertThat(PlazoDeModificacion.permite(INICIO, UN_DIA, INICIO.minus(Duration.ofDays(3)))).isTrue();
    }

    @Test
    void fueraDelPlazo() {
        assertThat(PlazoDeModificacion.permite(INICIO, UN_DIA, INICIO.minus(Duration.ofHours(20)))).isFalse();
    }

    @Test
    void justoEnElLimiteSePuede() {
        assertThat(PlazoDeModificacion.permite(INICIO, UN_DIA, INICIO.minus(Duration.ofHours(24)))).isTrue();
    }

    @Test
    void unSegundoDespuesDelLimiteNoSePuede() {
        assertThat(PlazoDeModificacion.permite(INICIO, UN_DIA, INICIO.minus(Duration.ofHours(24)).plusSeconds(1)))
                .isFalse();
    }

    @Test
    void elLimiteEsElInicioMenosLaAnticipacion() {
        assertThat(PlazoDeModificacion.limite(INICIO, UN_DIA)).isEqualTo(INICIO.minus(Duration.ofHours(24)));
    }
}
