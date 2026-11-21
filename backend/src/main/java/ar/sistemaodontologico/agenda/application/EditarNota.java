package ar.sistemaodontologico.agenda.application;

import static ar.sistemaodontologico.agenda.application.AgendaDelConsultorio.campos;

import ar.sistemaodontologico.agenda.domain.Turno;
import ar.sistemaodontologico.agenda.domain.TurnoId;
import ar.sistemaodontologico.compartido.application.PublicadorDeAuditoria;
import ar.sistemaodontologico.compartido.application.TenantContext;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import ar.sistemaodontologico.compartido.domain.auditoria.CambioAuditado;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nota interna del odontólogo en un turno, de hasta 500 caracteres (spec gestion-de-turnos, US-025). El evento lleva
 * la nota anterior y la nueva completas, como pide US-025 (decisión de Maxi en #38, design D8). Una nota igual no
 * escribe ni audita.
 */
@Service
public class EditarNota {

    public static final String ACCION = "TURNO_NOTA_CAMBIADA";

    private final TurnoRepositorio turnos;
    private final AgendaDelConsultorio agenda;
    private final LockDeAgenda lock;
    private final TenantContext tenant;
    private final PublicadorDeAuditoria auditoria;

    EditarNota(TurnoRepositorio turnos, AgendaDelConsultorio agenda, LockDeAgenda lock, TenantContext tenant,
            PublicadorDeAuditoria auditoria) {
        this.turnos = turnos;
        this.agenda = agenda;
        this.lock = lock;
        this.tenant = tenant;
        this.auditoria = auditoria;
    }

    @Transactional
    public TurnoGuardado editar(TurnoId id, long versionEsperada, String nota, Autor autor) {
        autor.exigirOdontologo();
        ConsultorioId consultorio = tenant.actual();
        lock.tomar(consultorio);
        Turno turno = agenda.turnoEnSuVersion(consultorio, id, versionEsperada);
        String anterior = turno.notaInterna();
        turno.editarNota(nota);
        if (Objects.equals(anterior, turno.notaInterna())) {
            return new TurnoGuardado(turno, versionEsperada);
        }
        long version = turnos.actualizar(turno, versionEsperada);
        auditoria.publicar(new CambioAuditado(consultorio, autor.actor(), CrearTurno.ENTIDAD, id.valor(), ACCION,
                campos("nota_interna", anterior), campos("nota_interna", turno.notaInterna()), autor.ipONull()));
        return new TurnoGuardado(turno, version);
    }
}
