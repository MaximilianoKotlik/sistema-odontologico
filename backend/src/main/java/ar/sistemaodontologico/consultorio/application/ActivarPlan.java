package ar.sistemaodontologico.consultorio.application;

import ar.sistemaodontologico.compartido.application.PublicadorDeAuditoria;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import ar.sistemaodontologico.compartido.domain.auditoria.Actor;
import ar.sistemaodontologico.compartido.domain.auditoria.CambioAuditado;
import ar.sistemaodontologico.compartido.domain.error.RecursoNoEncontrado;
import ar.sistemaodontologico.consultorio.domain.PlanDelConsultorio;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pase manual a {@code ACTIVO} (C-20, RN-PL-04, design D3): una operación interna del equipo, sin endpoint HTTP, que
 * audita {@code PLAN_ACTIVADO} con actor {@code SISTEMA}. Vale desde {@code PRUEBA} o {@code VENCIDO}.
 */
@Service
public class ActivarPlan {

    public static final String ACCION = "PLAN_ACTIVADO";

    private final ConsultorioRepositorio consultorios;
    private final PublicadorDeAuditoria auditoria;

    public ActivarPlan(ConsultorioRepositorio consultorios, PublicadorDeAuditoria auditoria) {
        this.consultorios = consultorios;
        this.auditoria = auditoria;
    }

    /**
     * Devuelve el plan anterior, o vacío si ya estaba activo (sin cambios ni evento).
     *
     * @throws RecursoNoEncontrado si el consultorio no existe
     */
    @Transactional
    public Optional<String> activar(ConsultorioId id) {
        Optional<String> anterior = consultorios.activarPlan(id);
        if (anterior.isEmpty()) {
            if (!consultorios.existe(id)) {
                throw new RecursoNoEncontrado("consultorio");
            }
            return anterior;
        }
        auditoria.publicar(new CambioAuditado(id, Actor.sistema(), "consultorio", id.valor(), ACCION,
                Map.of("plan", anterior.get()), Map.of("plan", PlanDelConsultorio.ACTIVO.name()), null));
        return anterior;
    }
}
