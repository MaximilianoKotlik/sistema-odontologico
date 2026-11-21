package ar.sistemaodontologico.compartido.application;

/**
 * Puerto de rate limiting por clave (IP, email normalizado o usuario). Lo usan el filtro de {@code /auth/**} y los
 * casos de uso de recuperación y cambio de contraseña (design D8).
 */
public interface LimitadorDeSolicitudes {

    ResultadoDeLimite consumir(LimiteDeSolicitudes limite, String clave);
}
