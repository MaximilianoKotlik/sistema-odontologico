package ar.sistemaodontologico.agenda.application;

import ar.sistemaodontologico.agenda.domain.PacienteId;
import ar.sistemaodontologico.compartido.domain.auditoria.Actor;
import ar.sistemaodontologico.compartido.domain.auditoria.ActorTipo;
import ar.sistemaodontologico.compartido.domain.error.ConflictoDeNegocio;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Quién pide la operación y desde qué IP (design D4). C-12 lo arma con el odontólogo de la sesión y C-15 con el
 * paciente del link: los casos de uso no leen la sesión, así sirven en las rutas públicas.
 */
public record Autor(Actor actor, Optional<String> ip) {

    public static final String CODIGO_ACCION_NO_PERMITIDA = "ACCION_NO_PERMITIDA";

    public Autor {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(ip, "ip");
        if (actor.tipo() == ActorTipo.SISTEMA) {
            throw new IllegalArgumentException("La agenda la modifican el odontólogo o el paciente");
        }
    }

    public static Autor odontologo(UUID usuario, Optional<String> ip) {
        return new Autor(new Actor(ActorTipo.ODONTOLOGO, Objects.requireNonNull(usuario, "usuario")), ip);
    }

    /** {@code paciente} es {@code null} cuando todavía no existe: el alta por el link usa el id que se le asigna. */
    public static Autor paciente(PacienteId paciente, Optional<String> ip) {
        return new Autor(new Actor(ActorTipo.PACIENTE, paciente == null ? null : paciente.valor()), ip);
    }

    public boolean esOdontologo() {
        return actor.tipo() == ActorTipo.ODONTOLOGO;
    }

    /** El actor del evento: un paciente sin id todavía queda con el del paciente recién registrado. */
    Actor actorPara(PacienteId paciente) {
        return actor.tipo() == ActorTipo.PACIENTE && actor.id() == null
                ? new Actor(ActorTipo.PACIENTE, paciente.valor())
                : actor;
    }

    String ipONull() {
        return ip.orElse(null);
    }

    /** Red de seguridad para las acciones que son solo del odontólogo (design D4): C-15 nunca las pide. */
    void exigirOdontologo() {
        if (!esOdontologo()) {
            throw new ConflictoDeNegocio(CODIGO_ACCION_NO_PERMITIDA, "La acción no está permitida");
        }
    }
}
