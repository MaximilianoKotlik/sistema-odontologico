package ar.sistemaodontologico.auth.infrastructure.seguridad;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Refresh y logout se autentican con la cookie, así que rechazan cualquier {@code Origin} distinto del frontend
 * propio con 403 {@code ORIGEN_NO_PERMITIDO}, sin tocar el token (design D10, defensa extra contra CSRF). Sin
 * cabecera {@code Origin} se dejan pasar: la cookie {@code SameSite=Strict} ya no viaja desde otro sitio.
 */
class FiltroDeOrigenDeSesion extends OncePerRequestFilter {

    private static final Set<String> RUTAS = Set.of("/api/v1/auth/refresh", "/api/v1/auth/logout");

    private final String origenPermitido;
    private final RespuestasDeSeguridad respuestas;

    FiltroDeOrigenDeSesion(String origenPermitido, RespuestasDeSeguridad respuestas) {
        this.origenPermitido = origenPermitido;
        this.respuestas = respuestas;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest peticion) {
        return !RUTAS.contains(peticion.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        String origen = peticion.getHeader(HttpHeaders.ORIGIN);
        if (origen != null && !origen.equals(origenPermitido)) {
            respuestas.escribir(peticion, respuesta, HttpStatus.FORBIDDEN, "ORIGEN_NO_PERMITIDO",
                    "Origen no permitido");
            return;
        }
        cadena.doFilter(peticion, respuesta);
    }
}
