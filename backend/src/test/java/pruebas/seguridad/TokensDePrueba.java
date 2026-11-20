package pruebas.seguridad;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * Solo de test (C-04): firma access tokens a mano para probar los casos válidos e inválidos sin pasar por el
 * login. La clave es la de test de {@code IntegracionPostgresTest}.
 */
public final class TokensDePrueba {

    private TokensDePrueba() {
    }

    public static String valido(String secretoBase64, UUID usuario, UUID consultorio, int version) {
        Instant ahora = Instant.now();
        return firmar(secretoBase64, JWSAlgorithm.HS256, usuario, consultorio, version, ahora, ahora.plusSeconds(900));
    }

    public static String vencido(String secretoBase64, UUID usuario, UUID consultorio) {
        Instant hace = Instant.now().minusSeconds(3600);
        return firmar(secretoBase64, JWSAlgorithm.HS256, usuario, consultorio, 1, hace, hace.plusSeconds(900));
    }

    public static String conAlgoritmo(String secretoBase64, JWSAlgorithm algoritmo, UUID usuario, UUID consultorio) {
        Instant ahora = Instant.now();
        return firmar(secretoBase64, algoritmo, usuario, consultorio, 1, ahora, ahora.plusSeconds(900));
    }

    public static String firmar(String secretoBase64, JWSAlgorithm algoritmo, UUID usuario, UUID consultorio,
            int version, Instant emitido, Instant vence) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(usuario.toString())
                    .claim("cid", consultorio.toString())
                    .claim("ver", version)
                    .issueTime(Date.from(emitido))
                    .expirationTime(Date.from(vence))
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(algoritmo), claims);
            jwt.sign(new MACSigner(Base64.getDecoder().decode(secretoBase64)));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }
}
