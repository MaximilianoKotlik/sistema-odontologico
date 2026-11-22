package ar.sistemaodontologico.consultorio.application;

import ar.sistemaodontologico.compartido.application.PublicadorDeAuditoria;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import ar.sistemaodontologico.compartido.domain.auditoria.Actor;
import ar.sistemaodontologico.compartido.domain.auditoria.CambioAuditado;
import ar.sistemaodontologico.consultorio.domain.PlanDelConsultorio;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Vencimiento diario de las pruebas (C-20, FL-07, design D2): pasa a {@code VENCIDO} cada prueba terminada y audita
 * {@code PLAN_VENCIDO} con actor {@code SISTEMA} en la misma transacción. No borra datos (RN-PL-03). Sin petición: el
 * consultorio sale de cada fila, nunca del {@code TenantContext}.
 */
@Service
public class VencerPruebas {

    public static final String ACCION = "PLAN_VENCIDO";

    private final ConsultorioRepositorio consultorios;
    private final PublicadorDeAuditoria auditoria;

    public VencerPruebas(ConsultorioRepositorio consultorios, PublicadorDeAuditoria auditoria) {
        this.consultorios = consultorios;
        this.auditoria = auditoria;
    }

    /** Devuelve los consultorios que pasaron a {@code VENCIDO} en esta corrida. */
    @Transactional
    public List<ConsultorioId> vencer(Instant ahora) {
        List<ConsultorioId> vencidos = consultorios.vencerPruebas(ahora);
        vencidos.forEach(id -> auditoria.publicar(new CambioAuditado(id, Actor.sistema(), "consultorio", id.valor(),
                ACCION, Map.of("plan", PlanDelConsultorio.PRUEBA.name()),
                Map.of("plan", PlanDelConsultorio.VENCIDO.name()), null)));
        return vencidos;
    }
}
