package ar.sistemaodontologico.compartido.domain.auditoria;

import java.util.Objects;
import java.util.UUID;

/** Autor de un cambio auditado: su tipo y, si lo tiene, su id (usuario o paciente). */
public record Actor(ActorTipo tipo, UUID id) {

    public Actor {
        Objects.requireNonNull(tipo, "tipo");
    }

    /** Cambios hechos por el sistema (tareas programadas, operaciones internas). */
    public static Actor sistema() {
        return new Actor(ActorTipo.SISTEMA, null);
    }
}
