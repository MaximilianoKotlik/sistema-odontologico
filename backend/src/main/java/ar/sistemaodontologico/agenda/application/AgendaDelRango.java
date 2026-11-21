package ar.sistemaodontologico.agenda.application;

import ar.sistemaodontologico.agenda.domain.Bloqueo;
import java.util.List;

/** Los turnos y bloqueos que intersectan un rango, cada lista ordenada por inicio (C-12, design D2). */
public record AgendaDelRango(List<TurnoDeAgenda> turnos, List<Bloqueo> bloqueos) {

    public AgendaDelRango {
        turnos = List.copyOf(turnos);
        bloqueos = List.copyOf(bloqueos);
    }
}
