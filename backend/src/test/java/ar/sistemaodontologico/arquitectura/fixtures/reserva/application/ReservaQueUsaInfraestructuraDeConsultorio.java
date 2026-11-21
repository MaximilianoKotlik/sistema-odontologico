package ar.sistemaodontologico.arquitectura.fixtures.reserva.application;

import ar.sistemaodontologico.arquitectura.fixtures.consultorio.infrastructure.ConsultorioJdbcDePrueba;

/** Fixture: viola la regla, porque reserva no usa la infraestructura de consultorio (C-15, design D1). */
public class ReservaQueUsaInfraestructuraDeConsultorio {

    private ConsultorioJdbcDePrueba repositorio;
}
