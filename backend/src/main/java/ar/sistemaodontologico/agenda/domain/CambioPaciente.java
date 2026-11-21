package ar.sistemaodontologico.agenda.domain;

/**
 * Qué hizo el paciente por el link en un turno que el odontólogo todavía no vio (C-12, design D3; DD-30). C-13 lo
 * muestra en la agenda.
 */
public enum CambioPaciente {
    RESERVO,
    REPROGRAMO,
    CANCELO
}
