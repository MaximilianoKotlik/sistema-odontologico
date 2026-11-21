package ar.sistemaodontologico.auth.infrastructure;

import ar.sistemaodontologico.auth.application.EnviadorDeEmails;
import ar.sistemaodontologico.auth.application.EnvioDeEmailFallido;
import ar.sistemaodontologico.auth.application.RecuperacionSolicitada;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Envía el email de recuperación después del commit y fuera del hilo de la petición (design D4): la respuesta 202
 * no espera al SMTP y su tiempo no revela si la cuenta existe. Si el envío falla, queda en el log sin el email ni el
 * token y no se reintenta: el odontólogo puede pedir otra recuperación.
 */
@Component
class EnvioDeRecuperacion {

    private static final Logger LOG = LoggerFactory.getLogger(EnvioDeRecuperacion.class);

    private final EnviadorDeEmails enviador;

    EnvioDeRecuperacion(EnviadorDeEmails enviador) {
        this.enviador = enviador;
    }

    @Async(ConfiguracionDeEnvioAsincrono.EJECUTOR_DE_EMAILS)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void enviar(RecuperacionSolicitada solicitud) {
        try {
            enviador.enviarRecuperacion(solicitud.email(), solicitud.token());
        } catch (EnvioDeEmailFallido e) {
            LOG.warn("No se pudo enviar el email de recuperación del usuario {}: {}", solicitud.usuario(),
                    e.getMessage());
        }
    }
}
