package ar.sistemaodontologico.agenda.application;

import static ar.sistemaodontologico.agenda.application.AgendaDelConsultorio.campos;

import ar.sistemaodontologico.agenda.domain.Bloqueo;
import ar.sistemaodontologico.agenda.domain.BloqueoId;
import ar.sistemaodontologico.agenda.domain.Intervalo;
import ar.sistemaodontologico.compartido.application.PublicadorDeAuditoria;
import ar.sistemaodontologico.compartido.application.TenantContext;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import ar.sistemaodontologico.compartido.domain.GeneradorDeIds;
import ar.sistemaodontologico.compartido.domain.auditoria.CambioAuditado;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El odontólogo bloquea un período de su agenda (spec gestion-de-bloqueos, US-012). No pisa turnos activos, tampoco
 * sobreturnos: si los hay, {@code BLOQUEO_CON_TURNOS} los devuelve todos (RN-AG-05). Puede superponerse con otros
 * bloqueos (C-10 D10).
 */
@Service
public class CrearBloqueo {

    public static final String ACCION = "BLOQUEO_CREADO";
    public static final String ENTIDAD = "bloqueo";

    private final BloqueoRepositorio bloqueos;
    private final AgendaDelConsultorio agenda;
    private final LockDeAgenda lock;
    private final TenantContext tenant;
    private final PublicadorDeAuditoria auditoria;
    private final GeneradorDeIds ids;

    CrearBloqueo(BloqueoRepositorio bloqueos, AgendaDelConsultorio agenda, LockDeAgenda lock, TenantContext tenant,
            PublicadorDeAuditoria auditoria, GeneradorDeIds ids) {
        this.bloqueos = bloqueos;
        this.agenda = agenda;
        this.lock = lock;
        this.tenant = tenant;
        this.auditoria = auditoria;
        this.ids = ids;
    }

    @Transactional
    public Bloqueo crear(Instant inicio, Instant fin, String motivo, Autor autor) {
        autor.exigirOdontologo();
        Bloqueo bloqueo = new Bloqueo(new BloqueoId(ids.nuevo()), new Intervalo(inicio, fin), motivo);
        ConsultorioId consultorio = tenant.actual();
        lock.tomar(consultorio);
        agenda.validarBloqueo(consultorio, bloqueo);
        bloqueos.insertar(consultorio, bloqueo);
        auditoria.publicar(new CambioAuditado(consultorio, autor.actor(), ENTIDAD, bloqueo.id().valor(), ACCION, null,
                comoMapa(bloqueo), autor.ipONull()));
        return bloqueo;
    }

    static java.util.Map<String, Object> comoMapa(Bloqueo bloqueo) {
        return campos("inicio", bloqueo.intervalo().inicio(), "fin", bloqueo.intervalo().fin(),
                "motivo", bloqueo.motivo());
    }
}
