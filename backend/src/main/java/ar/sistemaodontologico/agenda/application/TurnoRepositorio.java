package ar.sistemaodontologico.agenda.application;

import ar.sistemaodontologico.agenda.domain.Intervalo;
import ar.sistemaodontologico.agenda.domain.PacienteId;
import ar.sistemaodontologico.agenda.domain.Turno;
import ar.sistemaodontologico.agenda.domain.TurnoId;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Puerto de persistencia de los turnos; todo filtra por consultorio (regla dura 6, design D2). */
public interface TurnoRepositorio {

    Optional<TurnoGuardado> buscar(ConsultorioId consultorio, TurnoId id);

    /** Los turnos activos del consultorio, sobreturnos incluidos, que se solapan con el intervalo. */
    List<Turno> listarActivosQueIntersectan(ConsultorioId consultorio, Intervalo intervalo);

    /** Un turno nuevo, con versión 0, en el consultorio del propio turno. */
    void insertar(Turno turno);

    /**
     * Guarda el estado del turno si su versión sigue siendo la esperada y devuelve la nueva. Si cambió, lanza
     * {@code OptimisticLockingFailureException} (409 {@code CONFLICTO_CONCURRENCIA}, RN-GL-02) sin escribir.
     */
    long actualizar(Turno turno, long versionEsperada);

    /** Los turnos activos del paciente, reservados por link, que todavía no empezaron (RN-RP-08). */
    long contarFuturosActivosPorLink(ConsultorioId consultorio, PacienteId paciente, Instant ahora);

    /** Registra un cambio hecho por el paciente que el odontólogo todavía no vio (DD-30); no cambia la versión. */
    void marcarNovedadPaciente(ConsultorioId consultorio, TurnoId turno, Instant ahora);
}
