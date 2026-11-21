package ar.sistemaodontologico.reserva.infrastructure;

import ar.sistemaodontologico.compartido.application.LimitadorDeSolicitudes;
import ar.sistemaodontologico.compartido.application.LimiteDeSolicitudes;
import ar.sistemaodontologico.compartido.application.ResultadoDeLimite;
import ar.sistemaodontologico.compartido.web.ManejadorGlobalDeErrores;
import ar.sistemaodontologico.reserva.domain.TokenDeGestion;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Cupos de {@code /api/v1/public/**} antes de los controladores (RN-RP-09, design D9). Por IP: reservas y consultas.
 * Por token: reprogramaciones y cancelaciones, con el hash como clave para que el token no viva en el limitador. Esas
 * modificaciones también gastan el cupo de la IP: si no, probar tokens por POST no tendría límite. Además marca toda
 * respuesta por token como {@code no-store} (design D10).
 */
class LimitesPublicosFilter extends OncePerRequestFilter {

    private static final String DEMASIADAS_SOLICITUDES = "DEMASIADAS_SOLICITUDES";
    private static final String PREFIJO_TURNOS = "/api/v1/public/turnos/";
    private static final Pattern RESERVA = Pattern.compile("^/api/v1/public/consultorios/[^/]+/turnos/?$");
    private static final Pattern MODIFICACION =
            Pattern.compile("^/api/v1/public/turnos/([^/]+)/(?:reprogramacion|cancelacion)/?$");

    private final PropiedadesDeLimitesPublicos limites;
    private final LimitadorDeSolicitudes limitador;
    private final ObjectMapper json;

    LimitesPublicosFilter(PropiedadesDeLimitesPublicos limites, LimitadorDeSolicitudes limitador, ObjectMapper json) {
        this.limites = limites;
        this.limitador = limitador;
        this.json = json;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        String ruta = ruta(peticion);
        if (ruta.startsWith(PREFIJO_TURNOS)) {
            respuesta.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        }
        ResultadoDeLimite resultado = consumir(peticion, ruta);
        if (!resultado.permitido()) {
            rechazar(peticion, respuesta, resultado);
            return;
        }
        cadena.doFilter(peticion, respuesta);
    }

    private ResultadoDeLimite consumir(HttpServletRequest peticion, String ruta) {
        String ip = peticion.getRemoteAddr();
        if (HttpMethod.GET.matches(peticion.getMethod())) {
            return limitador.consumir(limites.limiteDeConsulta(), ip);
        }
        if (!HttpMethod.POST.matches(peticion.getMethod())) {
            return ResultadoDeLimite.permitir();
        }
        if (RESERVA.matcher(ruta).matches()) {
            return limitador.consumir(limites.limiteDeReserva(), ip);
        }
        Matcher modificacion = MODIFICACION.matcher(ruta);
        if (!modificacion.matches()) {
            return ResultadoDeLimite.permitir();
        }
        ResultadoDeLimite porIp = limitador.consumir(limites.limiteDeConsulta(), ip);
        if (!porIp.permitido()) {
            return porIp;
        }
        LimiteDeSolicitudes porToken = limites.limiteDeToken();
        return TokenDeGestion.leer(modificacion.group(1))
                .map(token -> limitador.consumir(porToken, token.hash()))
                .orElse(ResultadoDeLimite.permitir());
    }

    private void rechazar(HttpServletRequest peticion, HttpServletResponse respuesta, ResultadoDeLimite resultado)
            throws IOException {
        respuesta.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(resultado.segundosParaReintentar()));
        respuesta.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        respuesta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        json.writeValue(respuesta.getOutputStream(), ManejadorGlobalDeErrores.problemaPara(HttpStatus.TOO_MANY_REQUESTS,
                DEMASIADAS_SOLICITUDES, "Demasiadas solicitudes. Probá de nuevo en unos minutos",
                peticion.getRequestURI()));
    }

    private static String ruta(HttpServletRequest peticion) {
        String uri = peticion.getRequestURI();
        String contexto = peticion.getContextPath();
        return contexto == null || contexto.isEmpty() ? uri : uri.substring(contexto.length());
    }
}
