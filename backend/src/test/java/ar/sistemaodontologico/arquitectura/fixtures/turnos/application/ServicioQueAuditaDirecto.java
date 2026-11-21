package ar.sistemaodontologico.arquitectura.fixtures.turnos.application;

import ar.sistemaodontologico.arquitectura.fixtures.auditoria.application.RegistroDirectoDePrueba;

/** Fixture infractor: audita llamando al módulo de auditoría en lugar de publicar por el puerto de compartido. */
public class ServicioQueAuditaDirecto {
    private final RegistroDirectoDePrueba auditoria = new RegistroDirectoDePrueba();

    public void cancelar() {
        auditoria.registrar("TURNO_ESTADO_CAMBIADO");
    }
}
