package ar.sistemaodontologico.compartido.domain;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enmascara teléfonos, emails, DNI y tokens en textos que van a los logs (RN-GL-01). Es una red de seguridad para
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
    private static final Pattern TELEFONO = Pattern.compile("(?<![\\w-])\\+?\\d[\\d\\s()\\-]{5,}\\d(?![\\w-])");
    // Secretos de 256 bits o más (C-03): tokens en base64url (43+ caracteres) y hashes SHA-256 en hexadecimal.
    // Un UUID tiene 36 caracteres, así que queda legible.
    private static final Pattern TOKEN = Pattern.compile("(?<![A-Za-z0-9_-])[A-Za-z0-9_-]{43,}(?![A-Za-z0-9_-])");
    private static final String MASCARA_TOKEN = "***";
    // DNI escrito con puntos (DD-24): 7 u 8 dígitos como 9.000.001 o 30.000.001, aislado de otros dígitos y puntos.
    // Sin puntos ya lo cubre TELEFONO. Un monto con la misma forma (1.250.000) también se enmascara: se prefiere
    // perder legibilidad a filtrar un documento.
    private static final Pattern DNI_CON_PUNTOS = Pattern.compile("(?<![\\w.])\\d{1,2}\\.\\d{3}\\.\\d{3}(?![\\w]|\\.\\d)");
    private static final String MASCARA_DNI = "***";

    private EnmascaradorDePii() {
    }

    public static String enmascarar(String texto) {
        if (texto == null) {
            return null;
        }
        // Los tokens van primero: pueden contener tramos de dígitos que el patrón de teléfono cortaría a medias.
        String sinTokens = TOKEN.matcher(texto).replaceAll(MASCARA_TOKEN);
        String sinEmails = EMAIL.matcher(sinTokens).replaceAll(MASCARA_EMAIL);
        String sinDni = DNI_CON_PUNTOS.matcher(sinEmails).replaceAll(MASCARA_DNI);
        Matcher telefonos = TELEFONO.matcher(sinDni);
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
