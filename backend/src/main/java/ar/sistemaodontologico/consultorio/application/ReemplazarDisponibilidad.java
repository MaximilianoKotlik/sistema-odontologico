package ar.sistemaodontologico.consultorio.application;

import ar.sistemaodontologico.compartido.application.PublicadorDeAuditoria;
import ar.sistemaodontologico.compartido.application.SesionActual;
import ar.sistemaodontologico.compartido.application.TenantContext;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import ar.sistemaodontologico.compartido.domain.auditoria.Actor;
import ar.sistemaodontologico.compartido.domain.auditoria.ActorTipo;
import ar.sistemaodontologico.compartido.domain.auditoria.CambioAuditado;
import ar.sistemaodontologico.consultorio.domain.Franja;
import ar.sistemaodontologico.consultorio.domain.GrillaSemanal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consulta y reemplazo completo de la grilla semanal del consultorio de la sesión (US-011, spec
 * disponibilidad-semanal; design D2, D4 y D5). Toda la grilla se valida antes de tocar la base: una franja inválida
 * no deja cambios parciales.
 */
@Service
public class ReemplazarDisponibilidad {

    public static final String ACCION = "DISPONIBILIDAD_CAMBIADA";
    /** La grilla no tiene id propio: el evento usa el del consultorio (design D4). */
    public static final String ENTIDAD = "disponibilidad";

    private final DisponibilidadRepositorio disponibilidad;
    private final TenantContext tenant;
    private final SesionActual sesion;
    private final PublicadorDeAuditoria auditoria;

    public ReemplazarDisponibilidad(DisponibilidadRepositorio disponibilidad, TenantContext tenant,
            SesionActual sesion, PublicadorDeAuditoria auditoria) {
        this.disponibilidad = disponibilidad;
        this.tenant = tenant;
        this.sesion = sesion;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public GrillaSemanal consultar() {
        return disponibilidad.consultar(tenant.actual());
    }

    @Transactional
    public GrillaSemanal reemplazar(List<Franja> franjas) {
        ConsultorioId consultorio = tenant.actual();
        GrillaSemanal nueva = new GrillaSemanal(franjas);
        // Antes de leer la anterior: si otra pestaña guarda a la vez, esta espera y compara contra lo que quedó.
        disponibilidad.bloquear(consultorio);
        GrillaSemanal anterior = disponibilidad.consultar(consultorio);
        if (nueva.equals(anterior)) {
            return anterior;
        }
        disponibilidad.reemplazar(consultorio, nueva);
        auditoria.publicar(new CambioAuditado(consultorio, new Actor(ActorTipo.ODONTOLOGO, sesion.usuarioId()),
                ENTIDAD, consultorio.valor(), ACCION, anterior.comoMapaAuditable(), nueva.comoMapaAuditable(),
                sesion.ip().orElse(null)));
        return nueva;
    }
}
