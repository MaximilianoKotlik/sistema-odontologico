package ar.sistemaodontologico.arquitectura.fixtures.turnos.domain;

import org.springframework.context.ApplicationEventPublisher;

/**
 * Fixture infractor: el dominio depende de Spring.
 * Sin anotaciones de estereotipo para que el escaneo de componentes no lo registre.
 */
public class TurnoConSpring {
    private ApplicationEventPublisher publicador;
}
