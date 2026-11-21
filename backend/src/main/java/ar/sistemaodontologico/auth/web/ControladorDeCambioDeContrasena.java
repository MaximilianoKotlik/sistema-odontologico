package ar.sistemaodontologico.auth.web;

import ar.sistemaodontologico.auth.application.CambiarContrasena;
import ar.sistemaodontologico.compartido.application.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cambio de contraseña con sesión iniciada (US-005; C-05, design D7). Exige access token: el consultorio sale del
 * {@link TenantContext} y el usuario del {@code sub}. La ruta queda bajo el {@code Path} de la cookie de refresh,
 * así la cookie viaja y se borra desde acá: esa sesión también quedó revocada.
 */
@RestController
class ControladorDeCambioDeContrasena {

    record CambioRequest(String passwordActual, String passwordNueva) {

        @Override
        public String toString() {
            return "CambioRequest[]";
        }
    }

    private final CambiarContrasena cambio;
    private final TenantContext tenant;

    ControladorDeCambioDeContrasena(CambiarContrasena cambio, TenantContext tenant) {
        this.cambio = cambio;
        this.tenant = tenant;
    }

    @PostMapping("/api/v1/auth/cambiar-password")
    ResponseEntity<Void> cambiar(@RequestBody CambioRequest pedido, @AuthenticationPrincipal Jwt sesion,
            HttpServletRequest peticion) {
        cambio.cambiar(tenant.actual(), UUID.fromString(sesion.getSubject()), pedido.passwordActual(),
                pedido.passwordNueva(), peticion.getRemoteAddr());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, ControladorDeSesion.cookieBorrada().toString())
                .build();
    }
}
