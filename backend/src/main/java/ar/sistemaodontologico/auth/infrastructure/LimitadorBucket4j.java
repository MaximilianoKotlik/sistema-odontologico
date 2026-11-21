package ar.sistemaodontologico.auth.infrastructure;

import ar.sistemaodontologico.auth.application.LimitadorDeSolicitudes;
import ar.sistemaodontologico.auth.application.LimiteDeSolicitudes;
import ar.sistemaodontologico.auth.application.ResultadoDeLimite;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * Buckets de Bucket4j en memoria, uno por límite y clave. Caffeine los descarta tras una hora sin uso y acota la
 * cantidad de claves para que una ráfaga de IPs no agote la memoria (design D8). El tiempo sale del {@link Clock}
 * de la aplicación, así los tests lo controlan.
 */
@Component
public class LimitadorBucket4j implements LimitadorDeSolicitudes {

    private static final long MAXIMO_DE_CLAVES = 100_000;
    private static final Duration EXPIRACION_SIN_USO = Duration.ofHours(1);

    private final TimeMeter medidor;
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .maximumSize(MAXIMO_DE_CLAVES)
            .expireAfterAccess(EXPIRACION_SIN_USO)
            .build();

    public LimitadorBucket4j(Clock reloj) {
        this.medidor = new MedidorDeReloj(reloj);
    }

    @Override
    public ResultadoDeLimite consumir(LimiteDeSolicitudes limite, String clave) {
        Bucket bucket = buckets.get(limite.nombre() + ':' + clave, k -> nuevoBucket(limite));
        ConsumptionProbe intento = bucket.tryConsumeAndReturnRemaining(1);
        if (intento.isConsumed()) {
            return ResultadoDeLimite.permitir();
        }
        long segundos = TimeUnit.NANOSECONDS.toSeconds(intento.getNanosToWaitForRefill() + TimeUnit.SECONDS.toNanos(1) - 1);
        return ResultadoDeLimite.rechazar(segundos);
    }

    private Bucket nuevoBucket(LimiteDeSolicitudes limite) {
        return Bucket.builder()
                .addLimit(l -> l.capacity(limite.capacidad()).refillGreedy(limite.capacidad(), limite.periodo()))
                .withCustomTimePrecision(medidor)
                .build();
    }

    /** Adapta el {@link Clock} de la aplicación al medidor de tiempo de Bucket4j. */
    private record MedidorDeReloj(Clock reloj) implements TimeMeter {

        @Override
        public long currentTimeNanos() {
            Instant ahora = reloj.instant();
            return TimeUnit.SECONDS.toNanos(ahora.getEpochSecond()) + ahora.getNano();
        }

        @Override
        public boolean isWallClockBased() {
            return true;
        }
    }
}
