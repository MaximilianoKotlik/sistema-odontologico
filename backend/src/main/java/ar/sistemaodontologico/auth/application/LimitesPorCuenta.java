package ar.sistemaodontologico.auth.application;

import ar.sistemaodontologico.compartido.application.LimitadorDeSolicitudes;
import ar.sistemaodontologico.compartido.application.LimiteDeSolicitudes;
import ar.sistemaodontologico.compartido.application.ResultadoDeLimite;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Límites que aplican los casos de uso por cuenta, no por IP (design D8): así el filtro HTTP no tiene que leer el
 * cuerpo. La clave por email cuenta igual exista o no la cuenta, para que el 429 tampoco revele nada.
 */
public final class LimitesPorCuenta {

    private final LimitadorDeSolicitudes limitador;
    private final LimiteDeSolicitudes recuperacionPorEmail;
    private final LimiteDeSolicitudes cambiarPassword;

    public LimitesPorCuenta(LimitadorDeSolicitudes limitador, LimiteDeSolicitudes recuperacionPorEmail,
            LimiteDeSolicitudes cambiarPassword) {
        this.limitador = Objects.requireNonNull(limitador, "limitador");
        this.recuperacionPorEmail = Objects.requireNonNull(recuperacionPorEmail, "recuperacionPorEmail");
        this.cambiarPassword = Objects.requireNonNull(cambiarPassword, "cambiarPassword");
    }

    /** @throws LimiteExcedido si ese email ya pidió demasiadas recuperaciones */
    public void consumirRecuperacion(String email) {
        consumir(recuperacionPorEmail, email.trim().toLowerCase(Locale.ROOT));
    }

    /** @throws LimiteExcedido si ese usuario ya intentó demasiados cambios de contraseña */
    public void consumirCambioDeContrasena(UUID usuario) {
        consumir(cambiarPassword, usuario.toString());
    }

    private void consumir(LimiteDeSolicitudes limite, String clave) {
        ResultadoDeLimite resultado = limitador.consumir(limite, clave);
        if (!resultado.permitido()) {
            throw new LimiteExcedido(resultado.segundosParaReintentar());
        }
    }
}
