package ar.sistemaodontologico.auth.infrastructure.seguridad;

import ar.sistemaodontologico.auth.infrastructure.PropiedadesDeLimites;
import ar.sistemaodontologico.compartido.application.LimitadorDeSolicitudes;
import ar.sistemaodontologico.compartido.application.LimiteDeSolicitudes;
import ar.sistemaodontologico.compartido.application.ResultadoDeLimite;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Límite de solicitudes por IP en las rutas de autenticación (C-05, design D8). Va antes de la autenticación: un
 * 429 corta antes del caso de uso, así no cuenta como intento fallido de ninguna cuenta. La IP es
 * {@code getRemoteAddr()}: detrás de un proxy hay que configurar {@code server.forward-headers-strategy} (ver
 * backend/README.md).
 */
public class LimiteDeSolicitudesFilter extends OncePerRequestFilter {

    static final String DEMASIADAS_SOLICITUDES = "DEMASIADAS_SOLICITUDES";

    private final Map<String, LimiteDeSolicitudes> limitesPorRuta;
    private final LimitadorDeSolicitudes limitador;
    private final RespuestasDeSeguridad respuestas;

    public LimiteDeSolicitudesFilter(PropiedadesDeLimites limites, LimitadorDeSolicitudes limitador,
            RespuestasDeSeguridad respuestas) {
        this.limitesPorRuta = Map.of(
                "/api/v1/auth/login", limites.login(),
                "/api/v1/auth/registro", limites.registro(),
                "/api/v1/auth/refresh", limites.refresh(),
                "/api/v1/auth/recuperacion", limites.recuperacion(),
                "/api/v1/auth/recuperacion/confirmar", limites.confirmarRecuperacion());
        this.limitador = limitador;
        this.respuestas = respuestas;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest peticion) {
        return !HttpMethod.POST.matches(peticion.getMethod()) || !limitesPorRuta.containsKey(ruta(peticion));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        ResultadoDeLimite resultado = limitador.consumir(limitesPorRuta.get(ruta(peticion)), peticion.getRemoteAddr());
        if (!resultado.permitido()) {
            respuesta.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(resultado.segundosParaReintentar()));
            respuestas.escribir(peticion, respuesta, HttpStatus.TOO_MANY_REQUESTS, DEMASIADAS_SOLICITUDES,
                    "Demasiadas solicitudes. Probá de nuevo en unos minutos");
            return;
        }
        cadena.doFilter(peticion, respuesta);
    }

    private static String ruta(HttpServletRequest peticion) {
        String uri = peticion.getRequestURI();
        String contexto = peticion.getContextPath();
        String sinContexto = contexto == null || contexto.isEmpty() ? uri : uri.substring(contexto.length());
        return sinContexto.endsWith("/") && sinContexto.length() > 1
                ? sinContexto.substring(0, sinContexto.length() - 1)
                : sinContexto;
    }
}
