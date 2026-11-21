package ar.sistemaodontologico.agenda.domain;

/** Estados de un turno (spec turnos; el sobreturno es una marca, no un estado: DD-10). */
public enum EstadoTurno {
    RESERVADO,
    CONFIRMADO,
    CANCELADO,
    AUSENTE;

    /** Un turno activo ocupa su horario (RN-AG-01). */
    public boolean esActivo() {
        return this == RESERVADO || this == CONFIRMADO;
    }
}
