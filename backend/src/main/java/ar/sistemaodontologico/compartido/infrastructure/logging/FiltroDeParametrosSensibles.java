package ar.sistemaodontologico.compartido.infrastructure.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Enmascara con {@code ***} los parámetros de la query que pueden traer datos personales, para que no lleguen a los
 * logs (RN-GL-01). El resto de la cadena ve {@code getQueryString()} y {@code getRequestURL()} enmascarados: es lo que
 * escribe el {@code DispatcherServlet} en DEBUG. Los parámetros se siguen leyendo sin cambios con
 * {@code getParameter}, que el contenedor arma de la query original.
 *
 * <p>Hoy cubre {@code q} de la búsqueda de pacientes (C-12, design D7): puede ser un DNI, un teléfono o un nombre. No
 * alcanza a un log de acceso del contenedor (Tomcat lo arma con la petición original): si se habilita, se configura
 * su patrón sin {@code %q}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class FiltroDeParametrosSensibles extends OncePerRequestFilter {

    static final String MASCARA = "***";

    /** Ruta exacta → parámetros que se enmascaran. */
    private static final Map<String, Set<String>> SENSIBLES = Map.of("/api/v1/pacientes", Set.of("q"));

    @Override
    protected boolean shouldNotFilter(HttpServletRequest peticion) {
        return peticion.getQueryString() == null || !SENSIBLES.containsKey(peticion.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        cadena.doFilter(new PeticionEnmascarada(peticion, SENSIBLES.get(peticion.getRequestURI())), respuesta);
    }

    /** {@code a=1&q=90000001} → {@code a=1&q=***}. */
    static String enmascarar(String query, Set<String> sensibles) {
        return Arrays.stream(query.split("&", -1))
                .map(par -> {
                    int igual = par.indexOf('=');
                    String nombre = igual < 0 ? par : par.substring(0, igual);
                    return sensibles.contains(nombre) ? nombre + "=" + MASCARA : par;
                })
                .collect(Collectors.joining("&"));
    }

    private static final class PeticionEnmascarada extends HttpServletRequestWrapper {

        private final String query;

        PeticionEnmascarada(HttpServletRequest original, Set<String> sensibles) {
            super(original);
            this.query = enmascarar(original.getQueryString(), sensibles);
        }

        @Override
        public String getQueryString() {
            return query;
        }
    }
}
