package ar.sistemaodontologico.auth.domain;

import ar.sistemaodontologico.compartido.domain.error.ValidacionDeNegocio;
import java.util.Locale;

/**
 * RN-AU-01: al menos 10 caracteres, como máximo 72 bytes en UTF-8 (BCrypt no admite más y lanza una excepción) y
 * distinta del email. El error nunca incluye la contraseña.
 */
public final class PoliticaDeContrasena {

    public static final int LARGO_MINIMO = 10;
    public static final int BYTES_MAXIMOS = 72;

    private PoliticaDeContrasena() {
    }

    public static void validar(String contrasena, String email) {
        boolean corta = contrasena == null || contrasena.length() < LARGO_MINIMO;
        // En bytes y no en caracteres: una "ñ" o una vocal con tilde ocupan 2 bytes en UTF-8.
        boolean larga = contrasena != null
                && contrasena.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > BYTES_MAXIMOS;
        boolean igualAlEmail = contrasena != null && email != null
                && contrasena.toLowerCase(Locale.ROOT).equals(email.toLowerCase(Locale.ROOT));
        if (corta || larga || igualAlEmail) {
            throw new ValidacionDeNegocio("PASSWORD_DEBIL", "La contraseña no cumple la política");
        }
    }
}
