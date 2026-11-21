package ar.sistemaodontologico.agenda.application;

import static ar.sistemaodontologico.agenda.application.AgendaDelConsultorio.campos;

import ar.sistemaodontologico.agenda.domain.Duracion;
import ar.sistemaodontologico.agenda.domain.Turno;
import ar.sistemaodontologico.agenda.domain.TurnoId;
import ar.sistemaodontologico.compartido.application.PublicadorDeAuditoria;
import ar.sistemaodontologico.compartido.application.TenantContext;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import ar.sistemaodontologico.compartido.domain.auditoria.CambioAuditado;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El odontólogo cambia la duración de un turno activo, en múltiplos de 5 minutos, y se recalcula el fin (spec
 * gestion-de-turnos, US-024; RN-AG-12). El horario nuevo se valida como al crear; si falla, la duración no cambia.
 */
@Service
public class AjustarDuracion {

    public static final String ACCION = "TURNO_DURACION_CAMBIADA";

    private final TurnoRepositorio turnos;
    private final AgendaDelConsultorio agenda;
    private final LockDeAgenda lock;
    private final TenantContext tenant;
    private final PublicadorDeAuditoria auditoria;

    AjustarDuracion(TurnoRepositorio turnos, AgendaDelConsultorio agenda, LockDeAgenda lock, TenantContext tenant,
            PublicadorDeAuditoria auditoria) {
        this.turnos = turnos;
        this.agenda = agenda;
        this.lock = lock;
        this.tenant = tenant;
        this.auditoria = auditoria;
    }

    @Transactional
    public TurnoGuardado ajustar(TurnoId id, long versionEsperada, int minutos, Autor autor) {
        autor.exigirOdontologo();
        Duracion nueva = new Duracion(minutos);
        ConsultorioId consultorio = tenant.actual();
        lock.tomar(consultorio);
        Turno turno = agenda.turnoEnSuVersion(consultorio, id, versionEsperada);
        Duracion anterior = turno.duracion();
        Instant finAnterior = turno.fin();
        turno.ajustarDuracion(nueva);
        if (nueva.equals(anterior)) {
            return new TurnoGuardado(turno, versionEsperada);
        }
        agenda.validarTurno(consultorio, turno);
        long version = turnos.actualizar(turno, versionEsperada);
        auditoria.publicar(new CambioAuditado(consultorio, autor.actor(), CrearTurno.ENTIDAD, id.valor(), ACCION,
                campos("duracion_min", anterior.minutos(), "fin", finAnterior),
                campos("duracion_min", nueva.minutos(), "fin", turno.fin()), autor.ipONull()));
        return new TurnoGuardado(turno, version);
    }
}
