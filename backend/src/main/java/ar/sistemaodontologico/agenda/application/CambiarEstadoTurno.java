package ar.sistemaodontologico.agenda.application;

import static ar.sistemaodontologico.agenda.application.AgendaDelConsultorio.campos;

import ar.sistemaodontologico.agenda.domain.CanceladoPor;
import ar.sistemaodontologico.agenda.domain.EstadoTurno;
import ar.sistemaodontologico.agenda.domain.Turno;
import ar.sistemaodontologico.agenda.domain.TurnoId;
import ar.sistemaodontologico.compartido.application.PublicadorDeAuditoria;
import ar.sistemaodontologico.compartido.application.TenantContext;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import ar.sistemaodontologico.compartido.domain.auditoria.CambioAuditado;
import java.time.Clock;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Confirmar, cancelar o marcar ausente un turno según la máquina de estados de C-10 (spec gestion-de-turnos, US-022;
 * RN-AG-06). Cancelar guarda quién canceló según el actor. El motivo de cancelación no va al evento: es texto libre y
 * puede traer datos de salud (design D8).
 */
@Service
public class CambiarEstadoTurno {

    public static final String ACCION = "TURNO_ESTADO_CAMBIADO";

    private final TurnoRepositorio turnos;
    private final AgendaDelConsultorio agenda;
    private final LockDeAgenda lock;
    private final TenantContext tenant;
    private final PublicadorDeAuditoria auditoria;
    private final Clock reloj;

    CambiarEstadoTurno(TurnoRepositorio turnos, AgendaDelConsultorio agenda, LockDeAgenda lock, TenantContext tenant,
            PublicadorDeAuditoria auditoria, Clock reloj) {
        this.turnos = turnos;
        this.agenda = agenda;
        this.lock = lock;
        this.tenant = tenant;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    @Transactional
    public TurnoGuardado confirmar(TurnoId id, long versionEsperada, Autor autor) {
        return cambiar(id, versionEsperada, autor, Turno::confirmar);
    }

    @Transactional
    public TurnoGuardado cancelar(TurnoId id, long versionEsperada, String motivo, Autor autor) {
        CanceladoPor por = autor.esOdontologo() ? CanceladoPor.ODONTOLOGO : CanceladoPor.PACIENTE;
        return cambiar(id, versionEsperada, autor, turno -> turno.cancelar(por, motivo));
    }

    /** Solo cuando el turno ya empezó, con la hora del reloj del sistema. */
    @Transactional
    public TurnoGuardado marcarAusente(TurnoId id, long versionEsperada, Autor autor) {
        return cambiar(id, versionEsperada, autor, turno -> turno.marcarAusente(reloj.instant()));
    }

    private TurnoGuardado cambiar(TurnoId id, long versionEsperada, Autor autor, Consumer<Turno> cambio) {
        ConsultorioId consultorio = tenant.actual();
        lock.tomar(consultorio);
        Turno turno = agenda.turnoEnSuVersion(consultorio, id, versionEsperada);
        EstadoTurno estadoAnterior = turno.estado();
        CanceladoPor canceladoAntes = turno.canceladoPor();
        cambio.accept(turno);
        long version = turnos.actualizar(turno, versionEsperada);
        auditoria.publicar(new CambioAuditado(consultorio, autor.actor(), CrearTurno.ENTIDAD, id.valor(), ACCION,
                campos("estado", estadoAnterior.name(), "cancelado_por", nombre(canceladoAntes)),
                campos("estado", turno.estado().name(), "cancelado_por", nombre(turno.canceladoPor())),
                autor.ipONull()));
        return new TurnoGuardado(turno, version);
    }

    private static String nombre(Enum<?> valor) {
        return valor == null ? null : valor.name();
    }
}
