package ar.sistemaodontologico.auth.infrastructure.seguridad;

import ar.sistemaodontologico.auth.infrastructure.PropiedadesDeLimites;
import ar.sistemaodontologico.compartido.application.LimitadorDeSolicitudes;
import ar.sistemaodontologico.compartido.application.TenantContext;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * Cadena de seguridad stateless (C-04, spec seguridad-http, design D14): solo las rutas listadas son públicas y
 * todo lo demás exige un access token válido.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
class ConfiguracionDeSeguridad {

    /** Endpoints de sesión: se autentican con credenciales o con la cookie de refresh, no con el access token. */
    static final String[] RUTAS_DE_SESION = {
        "/api/v1/auth/registro", "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout",
        // Recuperación de contraseña (C-05): quien la usa no tiene sesión.
        "/api/v1/auth/recuperacion", "/api/v1/auth/recuperacion/confirmar"
    };

    private static final long UN_ANIO_EN_SEGUNDOS = 31_536_000L;

    /**
     * Solo con el perfil {@code local} (design D14, tarea 6.2): Swagger UI aplica estilos inline y usa íconos
     * {@code data:}, que {@code default-src 'self'} bloquea. Esta cadena relaja la CSP únicamente para sus rutas;
     * los scripts siguen limitados a {@code 'self'}. Fuera de local la cadena no existe y Swagger responde 404.
     */
    @Bean
    @org.springframework.core.annotation.Order(1)
    @org.springframework.context.annotation.Profile("local")
    SecurityFilterChain cadenaDeSwaggerLocal(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/swagger-ui.html", "/swagger-ui/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(cabeceras -> {
                    cabeceras.contentSecurityPolicy(csp -> csp.policyDirectives(
                            "default-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:"));
                    cabeceras.referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER));
                    cabeceras.httpStrictTransportSecurity(hsts -> hsts.disable());
                })
                .authorizeHttpRequests(rutas -> rutas.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    @org.springframework.core.annotation.Order(2)
    SecurityFilterChain cadenaDeSeguridad(HttpSecurity http, Environment entorno, TenantContext tenant,
            RespuestasDeSeguridad respuestas, PropiedadesDeAuth propiedades, PropiedadesDeLimites limites,
            LimitadorDeSolicitudes limitador) throws Exception {
        boolean local = entorno.acceptsProfiles(Profiles.of("local"));
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(Customizer.withDefaults())
                .headers(cabeceras -> {
                    cabeceras.contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'"));
                    cabeceras.referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER));
                    if (local) {
                        cabeceras.httpStrictTransportSecurity(hsts -> hsts.disable());
                    } else {
                        // Fuera de local siempre, también detrás de un proxy que termina TLS.
                        cabeceras.httpStrictTransportSecurity(hsts -> hsts
                                .requestMatcher(AnyRequestMatcher.INSTANCE)
                                .maxAgeInSeconds(UN_ANIO_EN_SEGUNDOS)
                                .includeSubDomains(true));
                    }
                })
                .authorizeHttpRequests(rutas -> {
                    rutas.requestMatchers(HttpMethod.POST, RUTAS_DE_SESION).permitAll();
                    rutas.requestMatchers("/api/v1/public/**").permitAll();
                    rutas.requestMatchers("/actuator/health", "/v3/api-docs", "/v3/api-docs/**").permitAll();
                    // Públicas en todos los perfiles: fuera de local springdoc las tiene apagadas y responden 404,
                    // que es lo que verifica ContratoOpenApiTest (C-01). Con un 401 no se distinguiría.
                    rutas.requestMatchers("/swagger-ui.html", "/swagger-ui/**").permitAll();
                    rutas.requestMatchers("/error").permitAll();
                    rutas.anyRequest().authenticated();
                })
                .oauth2ResourceServer(recurso -> recurso
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(respuestas.noAutenticado())
                        .accessDeniedHandler(respuestas.accesoDenegado()))
                .exceptionHandling(errores -> errores
                        .authenticationEntryPoint(respuestas.noAutenticado())
                        .accessDeniedHandler(respuestas.accesoDenegado()))
                // Antes que CORS: un origen ajeno en refresh/logout tiene que responder ORIGEN_NO_PERMITIDO en
                // Problem Details, no el 403 vacío del CorsFilter.
                .addFilterBefore(new FiltroDeOrigenDeSesion(propiedades.frontendOrigin(), respuestas), CorsFilter.class)
                // Primero de todos: un 429 no llega a la autenticación ni cuenta como intento fallido (C-05, D8).
                .addFilterBefore(new LimiteDeSolicitudesFilter(limites, limitador, respuestas),
                        FiltroDeOrigenDeSesion.class)
                .addFilterAfter(new FiltroDeConsultorioDeSesion(tenant), BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    /** CORS solo para el frontend propio, con credenciales para la cookie de refresh (spec seguridad-http).
     *  Spring Security lo encuentra por el nombre de bean {@code corsConfigurationSource}. */
    @Bean
    CorsConfigurationSource corsConfigurationSource(PropiedadesDeAuth propiedades) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(List.of(propiedades.frontendOrigin()));
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cors.setAllowCredentials(true);
        cors.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/**", cors);
        return fuente;
    }
}
