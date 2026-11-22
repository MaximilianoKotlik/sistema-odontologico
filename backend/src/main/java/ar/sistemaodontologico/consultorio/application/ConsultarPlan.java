package ar.sistemaodontologico.consultorio.application;

import ar.sistemaodontologico.compartido.application.TenantContext;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import ar.sistemaodontologico.compartido.domain.error.RecursoNoEncontrado;
import ar.sistemaodontologico.consultorio.application.ConsultorioRepositorio.PlanGuardado;
import ar.sistemaodontologico.consultorio.domain.EstadoDelPlan;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Estado efectivo del plan de un consultorio (C-20, design D1 y D5). Lo usan {@code GET /api/v1/plan}, la guarda de
 * escrituras y la reserva pública, así las tres aplican la misma regla de "vencido".
 */
@Service
public class ConsultarPlan {

    private final ConsultorioRepositorio consultorios;
    private final TenantContext tenant;
    private final Clock reloj;

    public ConsultarPlan(ConsultorioRepositorio consultorios, TenantContext tenant, Clock reloj) {
        this.consultorios = consultorios;
        this.tenant = tenant;
        this.reloj = reloj;
    }

    /** El plan del consultorio de la sesión. */
    @Transactional(readOnly = true)
    public EstadoDelPlan consultar() {
        return de(tenant.actual());
    }

    /** El plan de ese consultorio; falla con 404 si no existe. */
    @Transactional(readOnly = true)
    public EstadoDelPlan de(ConsultorioId consultorio) {
        PlanGuardado guardado = consultorios.buscarPlan(consultorio)
                .orElseThrow(() -> new RecursoNoEncontrado("consultorio"));
        return EstadoDelPlan.en(guardado.plan(), guardado.pruebaHasta(), ZoneId.of(guardado.zonaHoraria()),
                reloj.instant());
    }
}
