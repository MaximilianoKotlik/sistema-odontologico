package ar.sistemaodontologico.reserva.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Token con el que el paciente gestiona su turno (RN-RP-06, design D4): 256 bits en base64url sin relleno, 43
 * caracteres. En la base se guarda solo su {@link #hash()}, y {@link #toString()} no expone el valor.
 */
public final class TokenDeGestion {

    private static final Pattern FORMATO = Pattern.compile("^[A-Za-z0-9_-]{43}$");

    private final String valor;

    private TokenDeGestion(String valor) {
        this.valor = valor;
    }

    /** Un token recién generado por el generador de secretos. */
    public static TokenDeGestion emitido(String secreto) {
        return leer(secreto).orElseThrow(() -> new IllegalArgumentException("El secreto no tiene el formato del token"));
    }

    /** El token que llegó en una ruta; vacío si el formato no es válido, sin consultar la base (design D4). */
    public static Optional<TokenDeGestion> leer(String crudo) {
        return crudo != null && FORMATO.matcher(crudo).matches() ? Optional.of(new TokenDeGestion(crudo)) : Optional.empty();
    }

    public String valor() {
        return valor;
    }

    /** SHA-256 en hexadecimal: lo único que se persiste. */
    public String hash() {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    @Override
    public String toString() {
        return "TokenDeGestion[***]";
    }
}
