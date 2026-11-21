package ar.sistemaodontologico.reserva.infrastructure;

import ar.sistemaodontologico.compartido.application.LimitadorDeSolicitudes;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Filtros de {@code /api/v1/public/*} (design D9). Corren después de la cadena de Spring Security: así un 429 o un 413
 * salen con las mismas cabeceras de seguridad y CORS que el resto, sin tocar la configuración de {@code auth}.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PropiedadesDeLimitesPublicos.class)
class ConfiguracionDeApiPublica {

    private static final String RUTAS = "/api/v1/public/*";

    @Bean
    FilterRegistrationBean<LimitesPublicosFilter> filtroDeLimitesPublicos(PropiedadesDeLimitesPublicos limites,
            LimitadorDeSolicitudes limitador, ObjectMapper json) {
        FilterRegistrationBean<LimitesPublicosFilter> registro =
                new FilterRegistrationBean<>(new LimitesPublicosFilter(limites, limitador, json));
        registro.addUrlPatterns(RUTAS);
        registro.setOrder(SecurityProperties.DEFAULT_FILTER_ORDER + 1);
        return registro;
    }

    @Bean
    FilterRegistrationBean<TamanoMaximoDeCuerpoFilter> filtroDeTamanoDeCuerpo(ObjectMapper json) {
        FilterRegistrationBean<TamanoMaximoDeCuerpoFilter> registro =
                new FilterRegistrationBean<>(new TamanoMaximoDeCuerpoFilter(json));
        registro.addUrlPatterns(RUTAS);
        registro.setOrder(SecurityProperties.DEFAULT_FILTER_ORDER + 2);
        return registro;
    }
}
