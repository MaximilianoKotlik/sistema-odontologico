package ar.sistemaodontologico.auditoria.domain;

import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Posición de la paginación por keyset (C-18, design D5): {@code base64url(JSON).base64url(HMAC-SHA256)}. Se rechaza si
 * la firma no coincide o si es de otro consultorio.
 */
public record CursorDeAuditoria(Instant ocurridoAt, UUID id, ConsultorioId consultorio) {

    private static final String ALGORITMO = "HmacSHA256";
    private static final String CAMPO = "cursor";
    private static final Pattern CONTENIDO =
            Pattern.compile("^\\{\"o\":\"([^\"]+)\",\"i\":\"([^\"]+)\",\"c\":\"([^\"]+)\"}$");

    public CursorDeAuditoria {
        Objects.requireNonNull(ocurridoAt, "ocurridoAt");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(consultorio, "consultorio");
    }

    public String codificar(byte[] clave) {
        String json = "{\"o\":\"" + ocurridoAt + "\",\"i\":\"" + id + "\",\"c\":\"" + consultorio.valor() + "\"}";
        String contenido = base64().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        return contenido + "." + base64().encodeToString(firma(contenido, clave));
    }

    public static CursorDeAuditoria decodificar(String texto, byte[] clave, ConsultorioId consultorio) {
        String[] partes = texto.split("\\.", -1);
        if (partes.length != 2) {
            throw ErrorDeFiltro.en(CAMPO);
        }
        try {
            byte[] recibida = Base64.getUrlDecoder().decode(partes[1]);
            if (!MessageDigest.isEqual(recibida, firma(partes[0], clave))) {
                throw ErrorDeFiltro.en(CAMPO);
            }
            String json = new String(Base64.getUrlDecoder().decode(partes[0]), StandardCharsets.UTF_8);
            Matcher campos = CONTENIDO.matcher(json);
            if (!campos.matches() || !consultorio.valor().equals(UUID.fromString(campos.group(3)))) {
                throw ErrorDeFiltro.en(CAMPO);
            }
            return new CursorDeAuditoria(Instant.parse(campos.group(1)), UUID.fromString(campos.group(2)), consultorio);
        } catch (IllegalArgumentException | DateTimeException e) {
            throw ErrorDeFiltro.en(CAMPO);
        }
    }

    private static byte[] firma(String contenido, byte[] clave) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(new SecretKeySpec(clave, ALGORITMO));
            return mac.doFinal(contenido.getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo firmar el cursor");
        }
    }

    private static Base64.Encoder base64() {
        return Base64.getUrlEncoder().withoutPadding();
    }
}
