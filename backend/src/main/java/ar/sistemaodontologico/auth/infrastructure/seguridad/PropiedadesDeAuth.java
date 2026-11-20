package ar.sistemaodontologico.auth.infrastructure.seguridad;

import java.time.Duration;
import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

/**
 * Configuración de autenticación (C-04, design D2 y D7), validada al arrancar: con un secreto ausente,
 * no base64 o de menos de 32 bytes, la aplicación no levanta. Los mensajes de error nunca incluyen el secreto.
 *
 * @param jwtSecreto clave HS256 en base64 ({@code JWT_SECRET})
 * @param frontendOrigin único origen permitido por CORS y por el chequeo de {@code Origin} ({@code APP_FRONTEND_ORIGIN})
 * @param terminosVersionVigente versión de términos que se registra al aceptar ({@code TERMINOS_VERSION_VIGENTE})
 */
@ConfigurationProperties("app.auth")
public record PropiedadesDeAuth(
        String jwtSecreto,
        @DefaultValue("PT15M") Duration accessTtl,
        @DefaultValue("P14D") Duration refreshTtl,
        String frontendOrigin,
        @DefaultValue("2026-11-borrador") String terminosVersionVigente) {

    static final int BYTES_MINIMOS_DE_CLAVE = 32;

    public PropiedadesDeAuth {
        claveDesde(jwtSecreto);
        if (!StringUtils.hasText(frontendOrigin)) {
            throw new IllegalStateException("Falta APP_FRONTEND_ORIGIN");
        }
    }

    /** La clave HS256 decodificada. */
    public byte[] claveJwt() {
        return claveDesde(jwtSecreto);
    }

    private static byte[] claveDesde(String secreto) {
        if (!StringUtils.hasText(secreto)) {
            throw new IllegalStateException("Falta JWT_SECRET");
        }
        byte[] clave;
        try {
            clave = Base64.getDecoder().decode(secreto.trim());
        } catch (IllegalArgumentException e) {
            // Sin la causa: su mensaje puede citar caracteres del secreto.
            throw new IllegalStateException("JWT_SECRET no es base64 válido");
        }
        if (clave.length < BYTES_MINIMOS_DE_CLAVE) {
            throw new IllegalStateException("JWT_SECRET tiene que tener al menos " + BYTES_MINIMOS_DE_CLAVE + " bytes");
        }
        return clave;
    }
}
