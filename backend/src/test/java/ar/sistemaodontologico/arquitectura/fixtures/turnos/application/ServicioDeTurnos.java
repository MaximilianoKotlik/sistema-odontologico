package ar.sistemaodontologico.arquitectura.fixtures.turnos.application;

import ar.sistemaodontologico.arquitectura.fixtures.turnos.infrastructure.TurnoRepositorioJpa;

/** Fixture válido: un módulo usa su propia infraestructura. */
public class ServicioDeTurnos {
    private final TurnoRepositorioJpa repositorio = new TurnoRepositorioJpa();

    public String buscar() {
        return repositorio.buscar();
    }
}
