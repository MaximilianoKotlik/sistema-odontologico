package ar.sistemaodontologico.reserva.infrastructure;

import ar.sistemaodontologico.compartido.application.LimiteDeSolicitudes;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Cupos de la API pública, configurables en {@code app.rate-limit-publico.*} (RN-RP-09, design D9). */
@ConfigurationProperties("app.rate-limit-publico")
public record PropiedadesDeLimitesPublicos(Cupo reserva, Cupo consulta, Cupo token) {

    /** Capacidad y período tal como se escriben en la configuración. */
    public record Cupo(long capacidad, Duration periodo) {
    }

    LimiteDeSolicitudes limiteDeReserva() {
        return new LimiteDeSolicitudes("public-reserva", reserva.capacidad(), reserva.periodo());
    }

    LimiteDeSolicitudes limiteDeConsulta() {
        return new LimiteDeSolicitudes("public-consulta", consulta.capacidad(), consulta.periodo());
    }

    LimiteDeSolicitudes limiteDeToken() {
        return new LimiteDeSolicitudes("public-token", token.capacidad(), token.periodo());
    }
}
