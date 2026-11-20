package ar.sistemaodontologico.auth.infrastructure.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import ar.sistemaodontologico.auth.application.EmisorDeAccessToken.AccessToken;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** C-04, tarea 4.1: claims {@code sub}, {@code cid}, {@code ver}, {@code iat} y {@code exp} (+15 min), firma HS256. */
class EmisorDeAccessTokenNimbusTest {

    private static final Instant AHORA = Instant.parse("2026-12-01T12:00:00Z");
    private static final SecretKey CLAVE = new SecretKeySpec(new byte[48], "HmacSHA256");

    private final EmisorDeAccessTokenNimbus emisor = new EmisorDeAccessTokenNimbus(
            new NimbusJwtEncoder(new ImmutableSecret<>(CLAVE)), Clock.fixed(AHORA, ZoneOffset.UTC), Duration.ofMinutes(15));

    @Test
    void emiteUnTokenHs256ConLosClaimsDeLaSesion() {
        UUID usuario = UUID.randomUUID();
        ConsultorioId consultorio = new ConsultorioId(UUID.randomUUID());

        AccessToken token = emisor.emitir(usuario, consultorio, 3);

        Jwt jwt = decodificar(token.valor());
        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
        assertThat(jwt.getSubject()).isEqualTo(usuario.toString());
        assertThat(jwt.getClaimAsString("cid")).isEqualTo(consultorio.toString());
        assertThat(((Number) jwt.getClaim("ver")).intValue()).isEqualTo(3);
        assertThat(jwt.getIssuedAt()).isEqualTo(AHORA);
        assertThat(jwt.getExpiresAt()).isEqualTo(AHORA.plus(Duration.ofMinutes(15)));
        assertThat(token.vigencia()).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void elTokenNoLlevaDatosPersonales() {
        AccessToken token = emisor.emitir(UUID.randomUUID(), new ConsultorioId(UUID.randomUUID()), 1);

        assertThat(decodificar(token.valor()).getClaims()).containsOnlyKeys("sub", "cid", "ver", "iat", "exp");
    }

    private static Jwt decodificar(String valor) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(CLAVE).macAlgorithm(MacAlgorithm.HS256).build();
        // Sin validar el vencimiento: el reloj del emisor está fijo en 2026.
        decoder.setJwtValidator(jwt -> org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.success());
        return decoder.decode(valor);
    }
}
