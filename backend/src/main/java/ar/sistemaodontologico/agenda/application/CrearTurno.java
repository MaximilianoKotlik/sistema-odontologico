package ar.sistemaodontologico.agenda.application;

import static ar.sistemaodontologico.agenda.application.AgendaDelConsultorio.campos;

import ar.sistemaodontologico.agenda.domain.Duracion;
import ar.sistemaodontologico.agenda.domain.OrigenTurno;
import ar.sistemaodontologico.agenda.domain.Turno;
import ar.sistemaodontologico.agenda.domain.TurnoId;
import ar.sistemaodontologico.compartido.application.PublicadorDeAuditoria;
import ar.sistemaodontologico.compartido.application.TenantContext;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import ar.sistemaodontologico.compartido.domain.GeneradorDeIds;
import ar.sistemaodontologico.compartido.domain.auditoria.CambioAuditado;
import ar.sistemaodontologico.compartido.domain.error.ConflictoDeNegocio;
import ar.sistemaodontologico.compartido.domain.error.RecursoNoEncontrado;
import ar.sistemaodontologico.consultorio.application.PrestacionRepositorio;
import ar.sistemaodontologico.consultorio.domain.TipoDePrestacion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crea un turno manual, un sobreturno o el de una reserva pública (spec gestion-de-turnos, US-021; design D4, D5 y D8).
 * La duración es la vigente de la prestación (RN-AG-02); el odontólogo puede pedir otra. El turno se valida contra la
 * agenda con el lock tomado, y {@code ex_turno_sin_solapamiento} queda como red.
 */
@Service
public class CrearTurno {

    public static final String ACCION = "TURNO_CREADO";
    public static final String ENTIDAD = "turno";
    public static final String CODIGO_PRESTACION_INACTIVA = "PRESTACION_INACTIVA";

    private final TurnoRepositorio turnos;
    private final PacienteRepositorio pacientes;
    private final PrestacionRepositorio prestaciones;
    private final AgendaDelConsultorio agenda;
    private final LockDeAgenda lock;
    private final TenantContext tenant;
    private final PublicadorDeAuditoria auditoria;
    private final GeneradorDeIds ids;

    CrearTurno(TurnoRepositorio turnos, PacienteRepositorio pacientes, PrestacionRepositorio prestaciones,
            AgendaDelConsultorio agenda, LockDeAgenda lock, TenantContext tenant, PublicadorDeAuditoria auditoria,
            GeneradorDeIds ids) {
        this.turnos = turnos;
        this.pacientes = pacientes;
        this.prestaciones = prestaciones;
        this.agenda = agenda;
        this.lock = lock;
        this.tenant = tenant;
        this.auditoria = auditoria;
        this.ids = ids;
    }

    @Transactional
    public TurnoGuardado crear(NuevoTurno pedido, Autor autor) {
        // El sobreturno y la duración a mano son solo del odontólogo (RN-AG-04, RN-AG-02).
        if (pedido.sobreturno() || pedido.duracionMin().isPresent()) {
            autor.exigirOdontologo();
        }
        ConsultorioId consultorio = tenant.actual();
        lock.tomar(consultorio);
        pacientes.buscar(consultorio, pedido.paciente()).orElseThrow(() -> new RecursoNoEncontrado("paciente"));
        TipoDePrestacion prestacion = prestaciones.buscar(consultorio, pedido.prestacion())
                .orElseThrow(() -> new RecursoNoEncontrado("prestación"));
        if (!prestacion.activa()) {
            throw new ConflictoDeNegocio(CODIGO_PRESTACION_INACTIVA, "La prestación está desactivada");
        }
        Duracion duracion = new Duracion(pedido.duracionMin().orElse(prestacion.duracionMin()));
        TurnoId id = new TurnoId(ids.nuevo());
        Turno turno = pedido.sobreturno()
                ? Turno.nuevoSobreturno(id, consultorio, pedido.paciente(), prestacion.id(), pedido.inicio(), duracion)
                : Turno.nuevo(id, consultorio, pedido.paciente(), prestacion.id(), pedido.inicio(), duracion,
                        autor.esOdontologo() ? OrigenTurno.MANUAL : OrigenTurno.LINK);
        agenda.validarTurno(consultorio, turno);
        turnos.insertar(turno);
        auditoria.publicar(new CambioAuditado(consultorio, autor.actor(), ENTIDAD, id.valor(), ACCION, null,
                campos("inicio", turno.inicio(), "fin", turno.fin(), "duracion_min", duracion.minutos(),
                        "tipo_prestacion_id", prestacion.id().toString(),
                        "paciente_id", pedido.paciente().valor().toString(), "estado", turno.estado().name(),
                        "origen", turno.origen().name(), "es_sobreturno", turno.esSobreturno()),
                autor.ipONull()));
        return new TurnoGuardado(turno, 0);
    }
}
