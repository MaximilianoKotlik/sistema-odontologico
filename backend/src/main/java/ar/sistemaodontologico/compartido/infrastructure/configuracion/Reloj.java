package ar.sistemaodontologico.compartido.infrastructure.configuracion;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Único reloj del sistema, en UTC (C-02, design D2). Todo el código toma la hora de este bean; los tests
 * lo reemplazan por {@code Clock.fixed}. La conversión a la zona del consultorio se hace en presentación.
 */
@Configuration(proxyBeanMethods = false)
public class Reloj {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
