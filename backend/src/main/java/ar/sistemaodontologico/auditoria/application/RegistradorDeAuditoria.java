package ar.sistemaodontologico.auditoria.application;

import ar.sistemaodontologico.auditoria.domain.RegistroDeAuditoria;
import ar.sistemaodontologico.compartido.domain.GeneradorDeIds;
import ar.sistemaodontologico.compartido.domain.auditoria.EventoDeAuditoria;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Persiste cada evento que publica el puerto de auditoría de C-02 en la misma transacción del cambio, justo
 * antes del commit (C-03, design D-3). Si la inserción falla, la excepción aborta el commit y el cambio
 * auditado se revierte (RN-AD-03).
 */
@Component
public class RegistradorDeAuditoria {

    private final RepositorioDeAuditoria repositorio;
    private final GeneradorDeIds ids;

    public RegistradorDeAuditoria(RepositorioDeAuditoria repositorio, GeneradorDeIds ids) {
        this.repositorio = repositorio;
        this.ids = ids;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void registrar(EventoDeAuditoria evento) {
        repositorio.guardar(RegistroDeAuditoria.desde(evento, ids.nuevo()));
    }
}
