package ar.sistemaodontologico.auth.infrastructure.seguridad;

import ar.sistemaodontologico.compartido.application.TenantContext;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Establece el consultorio activo desde el claim {@code cid} del access token ya validado (RN-TE-01). Es la única
 * fuente del tenant en las rutas privadas: nunca el body, la query ni otra cabecera.
 */
class FiltroDeConsultorioDeSesion extends OncePerRequestFilter {

    private final TenantContext tenant;

    FiltroDeConsultorioDeSesion(TenantContext tenant) {
        this.tenant = tenant;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion instanceof JwtAuthenticationToken jwt) {
            String consultorio = jwt.getToken().getClaimAsString(ValidadorDeSesion.CLAIM_CONSULTORIO);
            tenant.establecer(new ConsultorioId(UUID.fromString(consultorio)));
        }
        cadena.doFilter(peticion, respuesta);
    }
}
