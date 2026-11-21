package pruebas.reloj;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Solo de test (C-05): reloj que arranca en la hora real y que el test adelanta a mano, para los escenarios con
 * vencimientos (fin del bloqueo, token vencido, recuperación del límite). Es seguro entre hilos.
 */
public final class RelojDePrueba extends Clock {

    private final AtomicReference<Instant> ahora = new AtomicReference<>(Clock.systemUTC().instant());

    public void avanzar(Duration duracion) {
        ahora.updateAndGet(actual -> actual.plus(duracion));
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
        return ahora.get();
    }
}
