package ar.sistemaodontologico.arquitectura.fixtures.reserva.application;

import ar.sistemaodontologico.arquitectura.fixtures.agenda.domain.AgendaPura;

/** Fixture: viola la regla, porque reserva habla con agenda solo por su capa application (C-15, design D1). */
public class ReservaQueUsaDominioDeAgenda {

    private AgendaPura agenda;
}
