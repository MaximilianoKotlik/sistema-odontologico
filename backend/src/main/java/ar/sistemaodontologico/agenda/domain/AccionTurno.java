package ar.sistemaodontologico.agenda.domain;

/** Acciones que cambian o exigen un estado del turno ({@link MaquinaDeEstadosTurno}). */
public enum AccionTurno {
    CONFIRMAR,
    CANCELAR,
    MARCAR_AUSENTE,
    REPROGRAMAR,
    AJUSTAR_DURACION
}
