package ar.sistemaodontologico.arquitectura.fixtures.pagos.application;

import ar.sistemaodontologico.arquitectura.fixtures.turnos.infrastructure.TurnoRepositorioJpa;

/** Fixture infractor: accede a la infraestructura de otro módulo. */
public class ServicioDePagos {
    private final TurnoRepositorioJpa repositorio = new TurnoRepositorioJpa();

    public String buscar() {
        return repositorio.buscar();
    }
}
