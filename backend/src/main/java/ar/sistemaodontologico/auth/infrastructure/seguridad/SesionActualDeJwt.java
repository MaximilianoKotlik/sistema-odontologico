package ar.sistemaodontologico.auth.infrastructure.seguridad;

import ar.sistemaodontologico.compartido.application.SesionActual;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** {@link SesionActual} desde el access token validado por la cadena de seguridad (C-08, design D3). */
@Component
class SesionActualDeJwt implements SesionActual {

    @Override
    public UUID usuarioId() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion instanceof JwtAuthenticationToken jwt) {
            return UUID.fromString(jwt.getToken().getSubject());
        }
        throw new IllegalStateException("La operación no tiene una sesión de odontólogo");
    }

    @Override
    public Optional<String> ip() {
        RequestAttributes atributos = RequestContextHolder.getRequestAttributes();
        if (atributos instanceof ServletRequestAttributes servlet) {
            return Optional.ofNullable(servlet.getRequest().getRemoteAddr());
        }
        return Optional.empty();
    }
}
