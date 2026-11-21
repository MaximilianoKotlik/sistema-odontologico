package ar.sistemaodontologico.agenda.application;

import ar.sistemaodontologico.agenda.domain.Intervalo;
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

    /**
     * Quita la marca de novedad del paciente solo si es la que se vio o una anterior, comparando en milisegundos: un
     * cliente que pasó el valor por {@code Date} lo manda truncado (C-12, design D5; nota de Maxi en #39). No toca la
     * versión ni otra columna (DD-39).
     */
    void limpiarNovedad(ConsultorioId consultorio, TurnoId id, Instant novedadVista);
}
