package ar.sistemaodontologico.arquitectura.fixtures.turnos.domain;

import jakarta.persistence.EntityManager;

/**
 * Fixture infractor: el dominio depende de la API de persistencia.
 * Sin {@code @Entity} para que Hibernate no lo escanee como entidad.
 */
public class TurnoConJpa {
    private EntityManager entityManager;
}
