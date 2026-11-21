package ar.sistemaodontologico.exportacion.application;

import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Una exportación en curso por consultorio, dentro de esta instancia (C-19, design D7). Entre instancias coordina el
 * lock consultivo de {@link CandadoDeExportacion}.
 *
 * <p>Si el streaming nunca arranca, nadie la libera: por eso vence sola pasado el timeout asíncrono más un margen. Cada
 * toma recuerda su pedido, para que una exportación vencida que termina tarde no libere la del pedido siguiente.
 */
@Component
public class GuardiaDeExportaciones {

    static final Duration MARGEN = Duration.ofMinutes(1);

    private record Toma(PedidoDeExportacion pedido, Instant vence) {

        private boolean vencida(Instant ahora) {
            return !ahora.isBefore(vence);
        }
    }

    private final Map<ConsultorioId, Toma> enCurso = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Duration maximo;

    public GuardiaDeExportaciones(Clock clock,
            @Value("${spring.mvc.async.request-timeout}") Duration timeoutAsincrono) {
        this.clock = clock;
        this.maximo = timeoutAsincrono.plus(MARGEN);
    }

    /** Toma la guardia; {@code false} si ese consultorio ya tiene una exportación en curso y sin vencer. */
    public boolean tomar(PedidoDeExportacion pedido) {
        Instant ahora = clock.instant();
        Toma nueva = new Toma(pedido, ahora.plus(maximo));
        return enCurso.compute(pedido.consultorio(),
                (consultorio, actual) -> actual == null || actual.vencida(ahora) ? nueva : actual) == nueva;
    }

    /** Libera la guardia solo si sigue siendo de este pedido. */
    public void liberar(PedidoDeExportacion pedido) {
        enCurso.computeIfPresent(pedido.consultorio(),
                (consultorio, actual) -> actual.pedido() == pedido ? null : actual);
    }

    public boolean estaTomada(ConsultorioId consultorio) {
        Toma toma = enCurso.get(consultorio);
        return toma != null && !toma.vencida(clock.instant());
    }
}
