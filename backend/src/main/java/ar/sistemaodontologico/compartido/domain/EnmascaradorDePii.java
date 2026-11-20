package ar.sistemaodontologico.compartido.domain;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enmascara teléfonos y emails en textos que van a los logs (RN-GL-01). Es una red de seguridad para
 * mensajes de excepciones inesperadas; el código no debe loguear datos personales a propósito.
 */
public final class EnmascaradorDePii {

    private static final String MASCARA_EMAIL = "***@***";
    private static final String MASCARA_TELEFONO = "***";
    private static final int DIGITOS_MINIMOS_TELEFONO = 7;

    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+");
    // Secuencia de dígitos con separadores típicos de teléfono, aislada (no parte de un UUID ni de una palabra).
    // Una fecha ISO (2026-11-20) tiene 8 dígitos con guiones, igual que un teléfono: se deja legible en el log.
    private static final Pattern FECHA_ISO = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern TELEFONO =Pattern.compile("(?<![\\w-])\\+?\\d[\\d\\s()\\-]{5,}\\d(?![\\w-])");

    private EnmascaradorDePii() {
    }

    public static String enmascarar(String texto) {
        if (texto == null) {
            return null;
        }
        String sinEmails = EMAIL.matcher(texto).replaceAll(MASCARA_EMAIL);
        Matcher telefonos = TELEFONO.matcher(sinEmails);
        StringBuilder resultado = new StringBuilder();
        while (telefonos.find()) {
            String candidato = telefonos.group();
            long digitos = candidato.chars().filter(Character::isDigit).count();
            boolean esTelefono = digitos >= DIGITOS_MINIMOS_TELEFONO && !FECHA_ISO.matcher(candidato).matches();
            String reemplazo = esTelefono ? MASCARA_TELEFONO : candidato;
            telefonos.appendReplacement(resultado, Matcher.quoteReplacement(reemplazo));
        }
        telefonos.appendTail(resultado);
        return resultado.toString();
    }
}
