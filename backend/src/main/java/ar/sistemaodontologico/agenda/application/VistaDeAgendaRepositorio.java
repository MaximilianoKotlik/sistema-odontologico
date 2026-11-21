package ar.sistemaodontologico.agenda.application;

import ar.sistemaodontologico.agenda.domain.Intervalo;
import ar.sistemaodontologico.agenda.domain.TurnoId;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Puerto de lectura de la agenda del odontólogo (C-12, design D1): turnos con su prestación y el nombre del paciente,
 * de solo lectura y sin el lock de agenda. Todo filtra por consultorio (regla dura 6).
 */
public interface VistaDeAgendaRepositorio {

    /** Los turnos del consultorio, en cualquier estado, que se solapan con el intervalo, ordenados por inicio. */
    List<TurnoDeAgenda> listarQueIntersectan(ConsultorioId consultorio, Intervalo intervalo);

    /** Los turnos activos que no son sobreturno y empiezan en {@code desde} o después, ordenados por inicio. */
    List<TurnoDeAgenda> listarActivosSinSobreturnoDesde(ConsultorioId consultorio, Instant desde);

    /** El detalle del turno sin el horario anterior, que sale de la auditoría; vacío si no es del consultorio. */
    Optional<DetalleDeTurno> buscarDetalle(ConsultorioId consultorio, TurnoId id);
}
