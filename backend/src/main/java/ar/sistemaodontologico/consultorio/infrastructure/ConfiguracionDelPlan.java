package ar.sistemaodontologico.consultorio.infrastructure;

import ar.sistemaodontologico.compartido.application.TenantContext;
import ar.sistemaodontologico.consultorio.application.ConsultarPlan;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Infraestructura del plan (C-20): las tareas programadas (hoy solo {@link TareaDeVencimiento}, design D2) y la guarda
 * de solo lectura (design D4).
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class ConfiguracionDelPlan {

    /**
     * Después de la cadena de Spring Security y de los filtros de la API pública (C-15, órdenes {@code +1} y
     * {@code +2}). Se registra en todas las rutas y el filtro decide con la misma ruta que usa Spring MVC: así el
     * {@code url-pattern} del contenedor no puede dejar afuera una variante que igual llegue a un controlador.
     */
    @Bean
    FilterRegistrationBean<FiltroDePlanVencido> filtroDePlanVencido(ConsultarPlan plan, TenantContext tenant,
            ObjectMapper json) {
        FilterRegistrationBean<FiltroDePlanVencido> registro =
                new FilterRegistrationBean<>(new FiltroDePlanVencido(plan, tenant, json));
        registro.addUrlPatterns("/*");
        registro.setOrder(SecurityProperties.DEFAULT_FILTER_ORDER + 3);
        return registro;
    }
}
