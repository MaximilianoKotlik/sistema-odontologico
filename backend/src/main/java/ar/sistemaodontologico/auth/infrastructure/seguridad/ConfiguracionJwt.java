package ar.sistemaodontologico.auth.infrastructure.seguridad;

import ar.sistemaodontologico.auth.application.VersionDeCredenciales;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import java.time.Duration;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** Firma y validación de access tokens HS256 con Nimbus (C-04, design D2). Sin código JWT propio. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PropiedadesDeAuth.class)
class ConfiguracionJwt {

    @Bean
    ar.sistemaodontologico.auth.application.TerminosVigentes terminosVigentes(PropiedadesDeAuth propiedades) {
        return propiedades::terminosVersionVigente;
    }

    @Bean
    SecretKey claveJwt(PropiedadesDeAuth propiedades) {
        return new SecretKeySpec(propiedades.claveJwt(), "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey clave) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(clave));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey clave, Clock reloj, VersionDeCredenciales versiones) {
        // Solo HS256: un token con otro algoritmo (HS512, none) se rechaza aunque la firma sea válida.
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(clave).macAlgorithm(MacAlgorithm.HS256).build();
        JwtTimestampValidator vencimiento = new JwtTimestampValidator(Duration.ZERO);
        vencimiento.setClock(reloj);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(vencimiento, new ValidadorDeSesion(versiones)));
        return decoder;
    }
}
