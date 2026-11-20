package ar.sistemaodontologico.compartido.application;

import ar.sistemaodontologico.compartido.domain.auditoria.CambioAuditado;

/**
 * Puerto por el que los casos de uso piden auditar un cambio (spec auditoria, design D10). Se llama dentro
 * de la transacción del cambio: si la transacción se revierte, el evento tampoco queda (RN-AD-03).
 */
public interface PublicadorDeAuditoria {

    /** Publica el cambio con el instante del reloj del sistema; falla si no hay una transacción activa. */
    void publicar(CambioAuditado cambio);
}
