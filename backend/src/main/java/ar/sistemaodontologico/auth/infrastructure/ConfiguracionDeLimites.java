package ar.sistemaodontologico.auth.infrastructure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registra los límites de solicitudes de {@code app.rate-limit.*} (design D8). */
@Configuration
@EnableConfigurationProperties(PropiedadesDeLimites.class)
class ConfiguracionDeLimites {
}
