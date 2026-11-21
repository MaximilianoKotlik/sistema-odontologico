package ar.sistemaodontologico.reserva.application;

/** Señal interna para revertir la transacción antes de armar el 409 con los huecos actualizados. */
final class HuecoTomado extends RuntimeException {

    HuecoTomado() {
        super(null, null, false, false);
    }
}
