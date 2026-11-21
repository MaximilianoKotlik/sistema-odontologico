package ar.sistemaodontologico.agenda.application;

import ar.sistemaodontologico.agenda.domain.Bloqueo;
import ar.sistemaodontologico.agenda.domain.BloqueoId;
import ar.sistemaodontologico.agenda.domain.Intervalo;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import java.util.List;
import java.util.Optional;

/** Puerto de persistencia de los bloqueos; todo filtra por consultorio (regla dura 6, design D2). */
public interface BloqueoRepositorio {

    Optional<Bloqueo> buscar(ConsultorioId consultorio, BloqueoId id);

    /** Los bloqueos del consultorio que se solapan con el intervalo. */
    List<Bloqueo> listarQueIntersectan(ConsultorioId consultorio, Intervalo intervalo);

    void insertar(ConsultorioId consultorio, Bloqueo bloqueo);

    /** Si existía en ese consultorio y se eliminó. */
    boolean eliminar(ConsultorioId consultorio, BloqueoId id);
}
