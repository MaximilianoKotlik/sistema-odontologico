package ar.sistemaodontologico.agenda.application;

import static ar.sistemaodontologico.agenda.application.AgendaDelConsultorio.campos;

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
 * Mueve un turno activo a otro inicio, con el mismo id y la misma duración (spec gestion-de-turnos, US-023;
 * RN-AG-11, RN-AG-12). El horario nuevo se valida como al crear, sin contar al propio turno. El odontólogo no tiene
 * anticipación mínima; la del paciente la valida C-15.
 */
@Service
public class ReprogramarTurno {

    public static final String ACCION = "TURNO_REPROGRAMADO";

    private final TurnoRepositorio turnos;
    private final AgendaDelConsultorio agenda;
    private final LockDeAgenda lock;
    private final TenantContext tenant;
    private final PublicadorDeAuditoria auditoria;

    ReprogramarTurno(TurnoRepositorio turnos, AgendaDelConsultorio agenda, LockDeAgenda lock, TenantContext tenant,
            PublicadorDeAuditoria auditoria) {
        this.turnos = turnos;
        this.agenda = agenda;
        this.lock = lock;
        this.tenant = tenant;
        this.auditoria = auditoria;
    }

    @Transactional
    public TurnoGuardado reprogramar(TurnoId id, long versionEsperada, Instant nuevoInicio, Autor autor) {
        ConsultorioId consultorio = tenant.actual();
        lock.tomar(consultorio);
        Turno turno = agenda.turnoEnSuVersion(consultorio, id, versionEsperada);
        Instant inicioAnterior = turno.inicio();
        Instant finAnterior = turno.fin();
        turno.reprogramar(nuevoInicio);
        // Al mismo inicio no cambia nada: sin escritura ni evento (design D8).
        if (turno.inicio().equals(inicioAnterior)) {
            return new TurnoGuardado(turno, versionEsperada);
        }
        agenda.validarTurno(consultorio, turno);
        long version = turnos.actualizar(turno, versionEsperada);
        auditoria.publicar(new CambioAuditado(consultorio, autor.actor(), CrearTurno.ENTIDAD, id.valor(), ACCION,
                campos("inicio", inicioAnterior, "fin", finAnterior), campos("inicio", turno.inicio(), "fin", turno.fin()),
                autor.ipONull()));
        return new TurnoGuardado(turno, version);
    }
}
