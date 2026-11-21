package ar.sistemaodontologico.exportacion.application;

import ar.sistemaodontologico.compartido.domain.ConsultorioId;

/** Segunda defensa con varias instancias (C-19, design D7): un lock que dura lo que la transacción del streaming. */
@FunctionalInterface
public interface CandadoDeExportacion {

    /** Intenta tomar el lock del consultorio sin esperar. */
    boolean tomar(ConsultorioId consultorio);
}
