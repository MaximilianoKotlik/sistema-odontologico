package ar.sistemaodontologico.auth.infrastructure.seguridad;

import ar.sistemaodontologico.auth.application.EmisorDeAccessToken;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

/** Access token HS256 con {@code sub}, {@code cid}, {@code ver}, {@code iat} y {@code exp}; sin datos personales. */
class EmisorDeAccessTokenNimbus implements EmisorDeAccessToken {

    private final JwtEncoder encoder;
    private final Clock reloj;
    private final Duration vigencia;

    EmisorDeAccessTokenNimbus(JwtEncoder encoder, Clock reloj, Duration vigencia) {
        this.encoder = encoder;
        this.reloj = reloj;
        this.vigencia = vigencia;
    }

    @Override
    public AccessToken emitir(UUID usuario, ConsultorioId consultorio, int versionDeCredenciales) {
        Instant ahora = Instant.now(reloj);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(usuario.toString())
                .claim(ValidadorDeSesion.CLAIM_CONSULTORIO, consultorio.toString())
                .claim(ValidadorDeSesion.CLAIM_VERSION, versionDeCredenciales)
                .issuedAt(ahora)
                .expiresAt(ahora.plus(vigencia))
                .build();
        JwsHeader cabecera = JwsHeader.with(MacAlgorithm.HS256).build();
        String valor = encoder.encode(JwtEncoderParameters.from(cabecera, claims)).getTokenValue();
        return new AccessToken(valor, vigencia);
    }
}
