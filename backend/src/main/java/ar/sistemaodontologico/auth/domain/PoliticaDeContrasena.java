package ar.sistemaodontologico.auth.domain;

import ar.sistemaodontologico.compartido.domain.error.ValidacionDeNegocio;
import java.util.Locale;

/** RN-AU-01: al menos 10 caracteres y distinta del email. El error nunca incluye la contraseña. */
public final class PoliticaDeContrasena {

    public static final int LARGO_MINIMO = 10;

    private PoliticaDeContrasena() {
    }

    public static void validar(String contrasena, String email) {
        boolean corta = contrasena == null || contrasena.length() < LARGO_MINIMO;
        boolean igualAlEmail = contrasena != null && email != null
                && contrasena.toLowerCase(Locale.ROOT).equals(email.toLowerCase(Locale.ROOT));
        if (corta || igualAlEmail) {
            throw new ValidacionDeNegocio("PASSWORD_DEBIL", "La contraseña no cumple la política");
        }
    }
}
