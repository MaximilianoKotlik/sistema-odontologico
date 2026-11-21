package ar.sistemaodontologico.auth.web;

/**
 * La cuenta está bloqueada por intentos fallidos (C-05, design D1). Se lanza en {@code auth.web}, después de que la
 * transacción del login confirmó el evento {@code LOGIN_FALLIDO}, y {@link ManejadorDeErroresDeAuth} la traduce a 423.
 */
class CuentaBloqueada extends RuntimeException {

    private final long minutosRestantes;

    CuentaBloqueada(long minutosRestantes) {
        super("Cuenta bloqueada", null, false, false);
        this.minutosRestantes = minutosRestantes;
    }

    long minutosRestantes() {
        return minutosRestantes;
    }
}
