package ar.sistemaodontologico.auth.infrastructure.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Puerto SesionActual de C-08 (tarea 1.1, design D3): el actor de la auditoría sale del access token validado. */
class SesionActualDeJwtTest {

    private final SesionActualDeJwt sesion = new SesionActualDeJwt();

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void elUsuarioSaleDelSubjectDelToken() {
        UUID usuario = UUID.randomUUID();
        autenticar(usuario);

        assertThat(sesion.usuarioId()).isEqualTo(usuario);
    }

    @Test
    void laIpSaleDeLaPeticion() {
        autenticar(UUID.randomUUID());
        MockHttpServletRequest peticion = new MockHttpServletRequest();
        peticion.setRemoteAddr("203.0.113.7");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(peticion));

        assertThat(sesion.ip()).contains("203.0.113.7");
    }

    @Test
    void sinPeticionNoHayIp() {
        autenticar(UUID.randomUUID());

        assertThat(sesion.ip()).isEmpty();
    }

    @Test
    void sinSesionEsUnErrorDeProgramacion() {
        assertThatThrownBy(sesion::usuarioId).isInstanceOf(IllegalStateException.class);
    }

    private static void autenticar(UUID usuario) {
        Jwt jwt = Jwt.withTokenValue("token-de-prueba")
                .header("alg", "HS256")
                .subject(usuario.toString())
                .claim("cid", UUID.randomUUID().toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
