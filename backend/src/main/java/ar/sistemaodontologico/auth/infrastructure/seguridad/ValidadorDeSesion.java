package ar.sistemaodontologico.auth.infrastructure.seguridad;

import ar.sistemaodontologico.auth.application.VersionDeCredenciales;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Valida que el token tenga {@code sub}, {@code cid} y {@code ver}, que el usuario pertenezca a ese consultorio y
 * que la versión de credenciales sea la vigente (spec sesion-odontologo, design D3). El motivo nunca llega al
 * cliente: cualquier falla responde el mismo 401.
 */
class ValidadorDeSesion implements OAuth2TokenValidator<Jwt> {

    static final String CLAIM_CONSULTORIO = "cid";
    static final String CLAIM_VERSION = "ver";

    private static final OAuth2TokenValidatorResult INVALIDO =
            OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));

    private final VersionDeCredenciales versiones;

    ValidadorDeSesion(VersionDeCredenciales versiones) {
        this.versiones = versiones;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        try {
            UUID usuario = UUID.fromString(token.getSubject());
            ConsultorioId consultorio = new ConsultorioId(UUID.fromString(token.getClaimAsString(CLAIM_CONSULTORIO)));
            Object version = token.getClaim(CLAIM_VERSION);
            if (!(version instanceof Number numero)) {
                return INVALIDO;
            }
            Optional<Integer> vigente = versiones.vigente(consultorio, usuario);
            return vigente.filter(v -> v == numero.intValue()).isPresent()
                    ? OAuth2TokenValidatorResult.success()
                    : INVALIDO;
        } catch (IllegalArgumentException | NullPointerException e) {
            return INVALIDO;
        }
    }
}
