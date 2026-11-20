package ar.sistemaodontologico.auth.infrastructure;

import ar.sistemaodontologico.auth.application.GeneradorDeSecretos;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

/** 32 bytes (256 bits) de {@link SecureRandom} en base64url sin relleno (design D4). */
@Component
class GeneradorDeSecretosSeguro implements GeneradorDeSecretos {

    private static final int BYTES = 32;

    private final SecureRandom aleatorio = new SecureRandom();

    @Override
    public String nuevo() {
        byte[] bytes = new byte[BYTES];
        aleatorio.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
