package ar.sistemaodontologico.agenda.application;

import ar.sistemaodontologico.compartido.domain.ConsultorioId;

/**
 * Serializa las escrituras de agenda de un consultorio (design D3). Bloquea hasta el fin de la transacción la misma
 * fila de {@code consultorio}, y en el mismo modo, que el reemplazo de la grilla de C-08: turnos, bloqueos, pacientes,
 * reservas (C-15) y cambios de disponibilidad quedan en una sola cola.
 *
 * <p>Se toma siempre primero, antes de leer lo que se va a validar. Repetirlo en la misma transacción no espera.
 */
public interface LockDeAgenda {

    void tomar(ConsultorioId consultorio);
}
