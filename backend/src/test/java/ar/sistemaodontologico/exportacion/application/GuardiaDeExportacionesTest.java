package ar.sistemaodontologico.exportacion.application;

import static org.assertj.core.api.Assertions.assertThat;

import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** La guardia vence sola si el streaming nunca llegó a liberarla (C-19, pedido de Maxi en el PR #54). */
class GuardiaDeExportacionesTest {

    private static final Instant INICIO = Instant.parse("2030-03-05T12:00:00Z");
    private static final Duration TIMEOUT_ASINCRONO = Duration.ofMinutes(5);
    private static final Duration MAXIMO = TIMEOUT_ASINCRONO.plus(GuardiaDeExportaciones.MARGEN);
    private static final UUID USUARIO = UUID.fromString("00000000-0000-7000-8000-000000000009");

    private final RelojDePrueba reloj = new RelojDePrueba();
    private final GuardiaDeExportaciones guardia = new GuardiaDeExportaciones(reloj, TIMEOUT_ASINCRONO);
    private final ConsultorioId consultorio = new ConsultorioId(UUID.randomUUID());

    @Test
    void unaGuardiaTomadaRechazaOtroPedidoAntesDelMaximo() {
        assertThat(guardia.tomar(pedido())).isTrue();

        reloj.avanzar(MAXIMO.minusNanos(1));

        assertThat(guardia.estaTomada(consultorio)).isTrue();
        assertThat(guardia.tomar(pedido())).isFalse();
    }

    @Test
    void unaGuardiaQueNuncaSeLiberaDejaExportarPasadoElMaximo() {
        guardia.tomar(pedido());

        reloj.avanzar(MAXIMO);

        assertThat(guardia.estaTomada(consultorio)).isFalse();
        assertThat(guardia.tomar(pedido())).isTrue();
    }

    @Test
    void laNuevaGuardiaCuentaSuPropioMaximo() {
        guardia.tomar(pedido());
        reloj.avanzar(MAXIMO);
        guardia.tomar(pedido());

        reloj.avanzar(MAXIMO.minusNanos(1));

        assertThat(guardia.estaTomada(consultorio)).isTrue();
    }

    @Test
    void unaExportacionVencidaQueTerminaTardeNoLiberaLaDelPedidoNuevo() {
        PedidoDeExportacion viejo = pedido();
        guardia.tomar(viejo);
        reloj.avanzar(MAXIMO);
        PedidoDeExportacion nuevo = pedido();
        guardia.tomar(nuevo);

        guardia.liberar(viejo);

        assertThat(guardia.estaTomada(consultorio)).isTrue();
        guardia.liberar(nuevo);
        assertThat(guardia.estaTomada(consultorio)).isFalse();
    }

    @Test
    void otroConsultorioNoComparteLaGuardia() {
        guardia.tomar(pedido());

        assertThat(guardia.tomar(new PedidoDeExportacion(new ConsultorioId(UUID.randomUUID()), USUARIO, null, INICIO)))
                .isTrue();
    }

    private PedidoDeExportacion pedido() {
        return new PedidoDeExportacion(consultorio, USUARIO, null, reloj.instant());
    }

    private static final class RelojDePrueba extends Clock {

        private Instant ahora = INICIO;

        void avanzar(Duration duracion) {
            ahora = ahora.plus(duracion);
        }

        @Override
        public Instant instant() {
            return ahora;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }
    }
}
