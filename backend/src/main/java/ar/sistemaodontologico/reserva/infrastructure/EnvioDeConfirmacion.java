package ar.sistemaodontologico.reserva.infrastructure;

import ar.sistemaodontologico.compartido.application.EnviadorDeEmails;
import ar.sistemaodontologico.compartido.application.EnvioDeEmailFallido;
import ar.sistemaodontologico.reserva.application.ReservaConfirmada;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Envía la confirmación después del commit y fuera del hilo de la petición (DD-26, design D12), en el ejecutor acotado
 * de emails. Si falla, queda un log sin el email ni el token, y la reserva sigue.
 */
@Component
class EnvioDeConfirmacion {

    private static final Logger LOG = LoggerFactory.getLogger(EnvioDeConfirmacion.class);
    private static final DateTimeFormatter FECHA =
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'a las' HH:mm", Locale.forLanguageTag("es-AR"));

    private final EnviadorDeEmails enviador;

    EnvioDeConfirmacion(EnviadorDeEmails enviador) {
        this.enviador = enviador;
    }

    @Async("ejecutorDeEmails")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void enviar(ReservaConfirmada reserva) {
        try {
            enviador.enviarConfirmacionDeTurno(reserva.email(), reserva.consultorio(),
                    FECHA.format(reserva.inicio().atZone(reserva.zona())), reserva.token());
        } catch (EnvioDeEmailFallido e) {
            LOG.warn("No se pudo enviar la confirmación de una reserva: {}", e.getMessage());
        }
    }
}
