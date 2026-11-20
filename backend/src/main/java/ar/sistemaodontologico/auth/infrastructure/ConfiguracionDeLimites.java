package ar.sistemaodontologico.auth.infrastructure;

import ar.sistemaodontologico.auth.application.LimitadorDeSolicitudes;
import ar.sistemaodontologico.auth.application.LimitesPorCuenta;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registra los límites de solicitudes de {@code app.rate-limit.*} (design D8). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PropiedadesDeLimites.class)
class ConfiguracionDeLimites {

    @Bean
    LimitesPorCuenta limitesPorCuenta(LimitadorDeSolicitudes limitador, PropiedadesDeLimites limites) {
        return new LimitesPorCuenta(limitador, limites.recuperacionPorEmail(), limites.cambiarPassword());
    }
}
