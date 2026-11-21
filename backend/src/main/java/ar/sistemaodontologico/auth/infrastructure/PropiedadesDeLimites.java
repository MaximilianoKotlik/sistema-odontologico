package ar.sistemaodontologico.auth.infrastructure;

import ar.sistemaodontologico.auth.application.LimiteDeSolicitudes;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Límites de solicitudes de autenticación, configurables en {@code app.rate-limit.*} (design D8). Cada límite toma
 * el nombre de su clave de configuración, así dos límites nunca comparten cupo.
 */
@ConfigurationProperties("app.rate-limit")
public final class PropiedadesDeLimites {

    private final LimiteDeSolicitudes login;
    private final LimiteDeSolicitudes registro;
    private final LimiteDeSolicitudes refresh;
    private final LimiteDeSolicitudes recuperacion;
    private final LimiteDeSolicitudes recuperacionPorEmail;
    private final LimiteDeSolicitudes confirmarRecuperacion;
    private final LimiteDeSolicitudes cambiarPassword;

    public PropiedadesDeLimites(Cupo login, Cupo registro, Cupo refresh, Cupo recuperacion,
                                Cupo recuperacionPorEmail, Cupo confirmarRecuperacion, Cupo cambiarPassword) {
        this.login = limite("login", login);
        this.registro = limite("registro", registro);
        this.refresh = limite("refresh", refresh);
        this.recuperacion = limite("recuperacion", recuperacion);
        this.recuperacionPorEmail = limite("recuperacion-por-email", recuperacionPorEmail);
        this.confirmarRecuperacion = limite("confirmar-recuperacion", confirmarRecuperacion);
        this.cambiarPassword = limite("cambiar-password", cambiarPassword);
    }

    /** Capacidad y período tal como se escriben en la configuración. */
    public record Cupo(long capacidad, Duration periodo) {
    }

    public LimiteDeSolicitudes login() {
        return login;
    }

    public LimiteDeSolicitudes registro() {
        return registro;
    }

    public LimiteDeSolicitudes refresh() {
        return refresh;
    }

    public LimiteDeSolicitudes recuperacion() {
        return recuperacion;
    }

    public LimiteDeSolicitudes recuperacionPorEmail() {
        return recuperacionPorEmail;
    }

    public LimiteDeSolicitudes confirmarRecuperacion() {
        return confirmarRecuperacion;
    }

    public LimiteDeSolicitudes cambiarPassword() {
        return cambiarPassword;
    }

    private static LimiteDeSolicitudes limite(String nombre, Cupo cupo) {
        return cupo == null ? null : new LimiteDeSolicitudes(nombre, cupo.capacidad(), cupo.periodo());
    }
}
