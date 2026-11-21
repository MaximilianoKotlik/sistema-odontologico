package ar.sistemaodontologico.arquitectura.fixtures.agenda.domain;

import ar.sistemaodontologico.arquitectura.fixtures.consultorio.domain.GrillaDePrueba;

/** Fixture (C-10, tarea 1.3): el dominio de agenda usando el de consultorio, que la regla prohíbe. */
public record AgendaQueUsaConsultorio(GrillaDePrueba grilla) {
}
