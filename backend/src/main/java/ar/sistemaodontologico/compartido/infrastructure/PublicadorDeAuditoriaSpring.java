package ar.sistemaodontologico.compartido.infrastructure;

import ar.sistemaodontologico.compartido.application.PublicadorDeAuditoria;
import ar.sistemaodontologico.compartido.domain.auditoria.CambioAuditado;
import ar.sistemaodontologico.compartido.domain.auditoria.EventoDeAuditoria;
import java.time.Clock;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Publica el cambio como evento de Spring, de forma síncrona y dentro de la transacción en curso. */
@Component
public class PublicadorDeAuditoriaSpring implements PublicadorDeAuditoria {

    private final ApplicationEventPublisher eventos;
    private final Clock reloj;

    public PublicadorDeAuditoriaSpring(ApplicationEventPublisher eventos, Clock reloj) {
        this.eventos = eventos;
        this.reloj = reloj;
    }

    @Override
    public void publicar(CambioAuditado cambio) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("La auditoría se publica dentro de la transacción del cambio");
        }
        eventos.publishEvent(new EventoDeAuditoria(Instant.now(reloj), cambio));
    }
}
