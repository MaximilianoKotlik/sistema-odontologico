package ar.sistemaodontologico.agenda.application;

import ar.sistemaodontologico.agenda.domain.Bloqueo;
import ar.sistemaodontologico.agenda.domain.BloqueoId;
import ar.sistemaodontologico.compartido.application.PublicadorDeAuditoria;
import ar.sistemaodontologico.compartido.application.TenantContext;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import ar.sistemaodontologico.compartido.domain.auditoria.CambioAuditado;
import ar.sistemaodontologico.compartido.domain.error.RecursoNoEncontrado;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El odontólogo elimina un bloqueo de su consultorio y su horario vuelve a estar disponible (spec gestion-de-bloqueos).
 * El evento guarda el bloqueo como valor anterior (RN-AD-04).
 */
@Service
public class EliminarBloqueo {

    public static final String ACCION = "BLOQUEO_ELIMINADO";

    private final BloqueoRepositorio bloqueos;
    private final LockDeAgenda lock;
    private final TenantContext tenant;
    private final PublicadorDeAuditoria auditoria;

    EliminarBloqueo(BloqueoRepositorio bloqueos, LockDeAgenda lock, TenantContext tenant,
            PublicadorDeAuditoria auditoria) {
        this.bloqueos = bloqueos;
        this.lock = lock;
        this.tenant = tenant;
        this.auditoria = auditoria;
    }

    @Transactional
    public void eliminar(BloqueoId id, Autor autor) {
        autor.exigirOdontologo();
        ConsultorioId consultorio = tenant.actual();
        lock.tomar(consultorio);
        Bloqueo bloqueo = bloqueos.buscar(consultorio, id).orElseThrow(() -> new RecursoNoEncontrado("bloqueo"));
        bloqueos.eliminar(consultorio, id);
        auditoria.publicar(new CambioAuditado(consultorio, autor.actor(), CrearBloqueo.ENTIDAD, id.valor(), ACCION,
                CrearBloqueo.comoMapa(bloqueo), null, autor.ipONull()));
    }
}
