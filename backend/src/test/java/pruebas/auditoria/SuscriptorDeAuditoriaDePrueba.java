package pruebas.auditoria;

import ar.sistemaodontologico.compartido.domain.auditoria.EventoDeAuditoria;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Solo de test: suscriptor que se comporta como el de C-03 (antes del commit, dentro de la transacción).
 * Registra los eventos que habría persistido.
 */
public class SuscriptorDeAuditoriaDePrueba {

    private final List<EventoDeAuditoria> confirmados = new CopyOnWriteArrayList<>();

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void antesDelCommit(EventoDeAuditoria evento) {
        confirmados.add(evento);
    }

    public List<EventoDeAuditoria> confirmados() {
        return confirmados;
    }

    public void limpiar() {
        confirmados.clear();
    }
}
