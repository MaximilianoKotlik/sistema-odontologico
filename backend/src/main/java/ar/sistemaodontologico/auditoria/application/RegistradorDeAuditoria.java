package ar.sistemaodontologico.auditoria.application;

import ar.sistemaodontologico.auditoria.domain.RegistroDeAuditoria;
import ar.sistemaodontologico.compartido.domain.GeneradorDeIds;
import ar.sistemaodontologico.compartido.domain.auditoria.CambioAuditado;
import ar.sistemaodontologico.compartido.domain.auditoria.EventoDeAuditoria;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger LOG = LoggerFactory.getLogger(RegistradorDeAuditoria.class);

    private final RepositorioDeAuditoria repositorio;
    private final GeneradorDeIds ids;

    public RegistradorDeAuditoria(RepositorioDeAuditoria repositorio, GeneradorDeIds ids) {
        this.repositorio = repositorio;
        this.ids = ids;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void registrar(EventoDeAuditoria evento) {
        try {
            repositorio.guardar(RegistroDeAuditoria.desde(evento, ids.nuevo()));
        } catch (RuntimeException e) {
            // Solo qué se quiso auditar, nunca los valores ni la excepción (su detalle puede traer datos).
            CambioAuditado cambio = evento.cambio();
            LOG.warn("No se pudo registrar el evento de auditoría accion={} entidad={} entidadId={}: se revierte el cambio",
                    cambio.accion(), cambio.entidad(), cambio.entidadId());
            throw e;
        }
    }
}
