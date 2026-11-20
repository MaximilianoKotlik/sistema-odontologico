package ar.sistemaodontologico.auditoria.application;

import ar.sistemaodontologico.auditoria.domain.RegistroDeAuditoria;

/**
 * Persistencia append-only del registro de auditoría (C-03, design D-2). Solo inserta: el registro no se
 * modifica ni se borra (RN-AD-02). La consulta, con tenant, llega en C-18.
 */
public interface RepositorioDeAuditoria {

    void guardar(RegistroDeAuditoria registro);
}
