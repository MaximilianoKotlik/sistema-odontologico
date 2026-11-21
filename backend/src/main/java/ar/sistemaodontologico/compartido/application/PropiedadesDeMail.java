package ar.sistemaodontologico.compartido.application;

import java.util.Objects;

/**
 * Remitente ({@code MAIL_FROM}) y origen del frontend para armar el link de recuperación (design D5). El origen es
 * el de {@code app.auth.frontend-origin}, que {@code PropiedadesDeAuth} (C-04) ya valida y normaliza.
 */
public record PropiedadesDeMail(String from, String frontendOrigin) {

    public PropiedadesDeMail {
        Objects.requireNonNull(from, "Falta MAIL_FROM");
        Objects.requireNonNull(frontendOrigin, "Falta APP_FRONTEND_ORIGIN");
    }
}
