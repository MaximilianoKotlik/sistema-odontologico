package ar.sistemaodontologico.compartido.domain.telefono;

import ar.sistemaodontologico.compartido.domain.error.ValidacionDeNegocio;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

/**
 * Normaliza teléfonos a E.164 con Argentina como región por defecto (spec telefono-e164).
 *
 * <p>Valida con {@code isPossibleNumber} (largo y estructura) y no con {@code isValidNumber} (rangos
 * asignados): los números ficticios de la regla dura 17 no están asignados (design D5).
 */
public final class NormalizadorDeTelefono {

    public static final String CODIGO_INVALIDO = "TELEFONO_INVALIDO";

    private static final String REGION_POR_DEFECTO = "AR";
    private static final PhoneNumberUtil UTIL = PhoneNumberUtil.getInstance();

    private NormalizadorDeTelefono() {
    }

    public static String normalizar(String texto) {
        if (texto == null || texto.isBlank()) {
            throw invalido();
        }
        try {
            PhoneNumber numero = UTIL.parse(texto, REGION_POR_DEFECTO);
            if (!UTIL.isPossibleNumber(numero)) {
                throw invalido();
            }
            return UTIL.format(numero, PhoneNumberFormat.E164);
        } catch (NumberParseException e) {
            throw invalido();
        }
    }

    // El mensaje nunca incluye el valor ingresado: es un dato personal (RN-GL-01).
    private static ValidacionDeNegocio invalido() {
        return new ValidacionDeNegocio(CODIGO_INVALIDO, "Teléfono inválido");
    }
}
