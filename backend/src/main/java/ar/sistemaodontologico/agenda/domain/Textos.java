package ar.sistemaodontologico.agenda.domain;

import ar.sistemaodontologico.compartido.domain.error.ValidacionDeNegocio;

/** Normalización de los textos del dominio de agenda. */
final class Textos {

    static final String CODIGO_VALIDACION = "VALIDACION";

    private Textos() {
    }

    /** Recortado; vacío pasa a {@code null}; más largo que el máximo es {@code VALIDACION}. */
    static String opcional(String texto, int largoMaximo) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String recortado = texto.strip();
        if (recortado.length() > largoMaximo) {
            throw invalido();
        }
        return recortado;
    }

    /** Recortado, de 1 al máximo de caracteres. */
    static String obligatorio(String texto, int largoMaximo) {
        String recortado = opcional(texto, largoMaximo);
        if (recortado == null) {
            throw invalido();
        }
        return recortado;
    }

    // El mensaje nunca incluye el valor: puede ser un dato personal (RN-GL-01).
    static ValidacionDeNegocio invalido() {
        return new ValidacionDeNegocio(CODIGO_VALIDACION, "Hay datos inválidos");
    }
}
