package ar.sistemaodontologico.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import ar.sistemaodontologico.auth.application.LimiteDeSolicitudes;
import ar.sistemaodontologico.auth.application.ResultadoDeLimite;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** Spec seguridad-http — Requirement: Límite de solicitudes en autenticación (design D8). */
class LimitadorBucket4jTest {

    private static final LimiteDeSolicitudes TRES_POR_HORA =
            new LimiteDeSolicitudes("recuperacion-email", 3, Duration.ofHours(1));
    private static final LimiteDeSolicitudes DIEZ_POR_MINUTO =
            new LimiteDeSolicitudes("login-ip", 10, Duration.ofMinutes(1));

    private final RelojAvanzable reloj = new RelojAvanzable(Instant.parse("2026-11-20T13:00:00Z"));
    private final LimitadorBucket4j limitador = new LimitadorBucket4j(reloj);

    @Test
    void permiteHastaLaCapacidadYRechazaLaSiguiente() {
        for (int i = 0; i < 3; i++) {
            assertThat(limitador.consumir(TRES_POR_HORA, "ana@example.com").permitido()).isTrue();
        }

        ResultadoDeLimite cuarta = limitador.consumir(TRES_POR_HORA, "ana@example.com");

        assertThat(cuarta.permitido()).isFalse();
    }

    @Test
    void alRechazarInformaLosSegundosHastaLaProximaSolicitud() {
        agotar(TRES_POR_HORA, "ana@example.com");

        ResultadoDeLimite rechazo = limitador.consumir(TRES_POR_HORA, "ana@example.com");

        // 3 por hora se reponen de a uno cada 20 minutos
        assertThat(rechazo.segundosParaReintentar()).isEqualTo(1200);
    }

    @Test
    void redondeaHaciaArribaLosSegundosParaReintentar() {
        agotar(TRES_POR_HORA, "ana@example.com");
        reloj.avanzar(Duration.ofMinutes(19).plusSeconds(59).plusMillis(500));

        ResultadoDeLimite rechazo = limitador.consumir(TRES_POR_HORA, "ana@example.com");

        assertThat(rechazo.permitido()).isFalse();
        assertThat(rechazo.segundosParaReintentar()).isEqualTo(1);
    }

    @Test
    void seRecuperaCuandoPasaElTiempoIndicado() {
        agotar(TRES_POR_HORA, "ana@example.com");
        long espera = limitador.consumir(TRES_POR_HORA, "ana@example.com").segundosParaReintentar();

        reloj.avanzar(Duration.ofSeconds(espera));

        assertThat(limitador.consumir(TRES_POR_HORA, "ana@example.com").permitido()).isTrue();
    }

    @Test
    void cadaClaveTieneSuPropioLimite() {
        agotar(TRES_POR_HORA, "ana@example.com");

        assertThat(limitador.consumir(TRES_POR_HORA, "beto@example.com").permitido()).isTrue();
    }

    @Test
    void limitesDistintosNoCompartenElCupoDeUnaMismaClave() {
        agotar(TRES_POR_HORA, "203.0.113.7");

        assertThat(limitador.consumir(DIEZ_POR_MINUTO, "203.0.113.7").permitido()).isTrue();
    }

    @Test
    void elPermitidoNoPideEspera() {
        ResultadoDeLimite permitido = limitador.consumir(DIEZ_POR_MINUTO, "203.0.113.7");

        assertThat(permitido.segundosParaReintentar()).isZero();
    }

    private void agotar(LimiteDeSolicitudes limite, String clave) {
        for (int i = 0; i < limite.capacidad(); i++) {
            assertThat(limitador.consumir(limite, clave).permitido()).isTrue();
        }
    }

    /** Reloj fijo que el test hace avanzar a mano. */
    private static final class RelojAvanzable extends Clock {

        private Instant ahora;

        RelojAvanzable(Instant inicio) {
            this.ahora = inicio;
        }

        void avanzar(Duration duracion) {
            ahora = ahora.plus(duracion);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }
}
