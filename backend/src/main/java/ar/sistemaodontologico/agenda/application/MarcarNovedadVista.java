package ar.sistemaodontologico.agenda.application;

import ar.sistemaodontologico.agenda.domain.TurnoId;
import ar.sistemaodontologico.compartido.application.TenantContext;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import ar.sistemaodontologico.compartido.domain.error.RecursoNoEncontrado;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El odontólogo marca como vista la novedad del paciente que recibió en la agenda o en el detalle (C-12, design D5;
 * DD-30). Si mientras tanto llegó una más nueva, no la borra: responde igual y el próximo {@code GET} la muestra.
 *
 * <p>No toma el lock de agenda ni usa la {@code version}: no cambia horarios ni estados. Sin evento de auditoría:
 * {@code novedad_paciente_at} es una marca de lectura de la interfaz y el cambio que la puso ya está auditado
 * (DD-39, excepción acotada a la regla dura 7).
 */
@Service
public class MarcarNovedadVista {

    private final TurnoRepositorio turnos;
    private final TenantContext tenant;

    MarcarNovedadVista(TurnoRepositorio turnos, TenantContext tenant) {
        this.turnos = turnos;
        this.tenant = tenant;
    }

    @Transactional
    public void marcar(TurnoId id, Instant novedadVista) {
        Objects.requireNonNull(novedadVista, "novedadVista");
        ConsultorioId consultorio = tenant.actual();
        turnos.buscar(consultorio, id).orElseThrow(() -> new RecursoNoEncontrado("turno"));
        turnos.limpiarNovedad(consultorio, id, novedadVista);
    }
}
