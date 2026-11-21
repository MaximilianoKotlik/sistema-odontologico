package ar.sistemaodontologico.agenda.application;

import ar.sistemaodontologico.agenda.domain.Bloqueo;
import ar.sistemaodontologico.agenda.domain.CambioPaciente;
import ar.sistemaodontologico.agenda.domain.DisponibilidadSemanal;
import ar.sistemaodontologico.agenda.domain.EstadoTurno;
import ar.sistemaodontologico.agenda.domain.Intervalo;
import ar.sistemaodontologico.agenda.domain.TurnoId;
import ar.sistemaodontologico.compartido.application.ConsultaDeAuditoria;
import ar.sistemaodontologico.compartido.application.ConsultaDeAuditoria.EventoRegistrado;
import ar.sistemaodontologico.compartido.application.TenantContext;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import ar.sistemaodontologico.compartido.domain.auditoria.ActorTipo;
import ar.sistemaodontologico.compartido.domain.error.RecursoNoEncontrado;
import ar.sistemaodontologico.compartido.domain.error.ValidacionDeNegocio;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lecturas de la agenda del odontólogo (C-12, spec agenda-del-odontologo; US-020, DD-30). Son de solo lectura y no
 * toman el lock de agenda (design D1).
 */
@Service
public class ConsultarAgenda {

    /** Cubre la vista semanal y un mes con margen (design D2, aprobado por Maxi en #39). */
    public static final Duration RANGO_MAXIMO = Duration.ofDays(42);

    private final VistaDeAgendaRepositorio vista;
    private final BloqueoRepositorio bloqueos;
    private final ConsultaDeAuditoria auditoria;
    private final TenantContext tenant;
    private final ConsultaDeDisponibilidad disponibilidad;
    private final Clock reloj;

    ConsultarAgenda(VistaDeAgendaRepositorio vista, BloqueoRepositorio bloqueos, ConsultaDeAuditoria auditoria,
            TenantContext tenant, ConsultaDeDisponibilidad disponibilidad, Clock reloj) {
        this.vista = vista;
        this.bloqueos = bloqueos;
        this.auditoria = auditoria;
        this.tenant = tenant;
        this.disponibilidad = disponibilidad;
        this.reloj = reloj;
    }

    /** Turnos en cualquier estado y bloqueos que intersectan {@code [desde, hasta)}, de hasta 42 días (D2). */
    @Transactional(readOnly = true)
    public AgendaDelRango agenda(Instant desde, Instant hasta) {
        Objects.requireNonNull(desde, "desde");
        Objects.requireNonNull(hasta, "hasta");
        if (!hasta.isAfter(desde) || Duration.between(desde, hasta).compareTo(RANGO_MAXIMO) > 0) {
            throw new ValidacionDeNegocio("VALIDACION", "El rango de la agenda no es válido");
        }
        ConsultorioId consultorio = tenant.actual();
        Intervalo rango = new Intervalo(desde, hasta);
        return new AgendaDelRango(conCambios(consultorio, vista.listarQueIntersectan(consultorio, rango)),
                bloqueos.listarQueIntersectan(consultorio, rango));
    }

    /**
     * El detalle del turno con el horario anterior si el paciente lo reprogramó: el {@code valor_anterior} de su último
     * {@code TURNO_REPROGRAMADO} (design D3 y D4). Un turno de otro consultorio es 404 (RN-TE-04).
     */
    @Transactional(readOnly = true)
    public DetalleDeTurno detalle(TurnoId id) {
        ConsultorioId consultorio = tenant.actual();
        DetalleDeTurno detalle = vista.buscarDetalle(consultorio, id)
                .orElseThrow(() -> new RecursoNoEncontrado("turno"));
        Optional<Intervalo> anterior = auditoria.buscarUltimo(consultorio, CrearTurno.ENTIDAD, id.valor(),
                ActorTipo.PACIENTE, ReprogramarTurno.ACCION).map(ConsultarAgenda::horarioAnterior);
        return detalle.con(conCambios(consultorio, List.of(detalle.turno())).get(0), anterior);
    }

    /**
     * Los turnos futuros activos, no sobreturnos, que la disponibilidad vigente no cubre (design D9, RN-AG-03). No
     * cambia nada: reemplazar la grilla no mueve ni cancela turnos (decisión de Maxi en #15); esto alimenta el aviso de
     * C-14. Sin tope de fecha: los turnos futuros de un consultorio son pocos.
     */
    @Transactional(readOnly = true)
    public List<TurnoDeAgenda> fueraDeDisponibilidad() {
        ConsultorioId consultorio = tenant.actual();
        DisponibilidadSemanal grilla = disponibilidad.de(consultorio);
        return conCambios(consultorio, vista.listarActivosSinSobreturnoDesde(consultorio, reloj.instant()).stream()
                .filter(turno -> !grilla.cubre(new Intervalo(turno.inicio(), turno.fin())))
                .toList());
    }

    /** Los bloqueos que terminan después de {@code desde} (o de ahora), ordenados por inicio (design D8). */
    @Transactional(readOnly = true)
    public List<Bloqueo> bloqueosVigentes(Optional<Instant> desde) {
        return bloqueos.listarVigentesDesde(tenant.actual(), desde.orElseGet(reloj::instant));
    }

    private static Intervalo horarioAnterior(EventoRegistrado reprogramacion) {
        return new Intervalo(Instant.parse((String) reprogramacion.valorAnterior().get("inicio")),
                Instant.parse((String) reprogramacion.valorAnterior().get("fin")));
    }

    /**
     * Completa el cambio del paciente de los turnos con novedad sin ver, con una sola consulta a la auditoría: el
     * último evento del turno con actor {@code PACIENTE} dice qué hizo (design D3).
     */
    private List<TurnoDeAgenda> conCambios(ConsultorioId consultorio, List<TurnoDeAgenda> turnos) {
        List<UUID> conNovedad = turnos.stream()
                .filter(turno -> turno.novedadPacienteAt() != null)
                .map(TurnoDeAgenda::id)
                .toList();
        if (conNovedad.isEmpty()) {
            return turnos;
        }
        Map<UUID, EventoRegistrado> ultimos = auditoria.buscarUltimosPorActor(consultorio, CrearTurno.ENTIDAD,
                conNovedad, ActorTipo.PACIENTE);
        return turnos.stream()
                .map(turno -> turno.novedadPacienteAt() == null || !ultimos.containsKey(turno.id())
                        ? turno
                        : turno.conCambio(cambioDe(ultimos.get(turno.id()))))
                .toList();
    }

    /** {@code TURNO_CREADO} → reservó, {@code TURNO_REPROGRAMADO} → reprogramó y la cancelación → canceló. */
    static CambioPaciente cambioDe(EventoRegistrado evento) {
        return switch (evento.accion()) {
            case CrearTurno.ACCION -> CambioPaciente.RESERVO;
            case ReprogramarTurno.ACCION -> CambioPaciente.REPROGRAMO;
            case CambiarEstadoTurno.ACCION ->
                    EstadoTurno.CANCELADO.name().equals(evento.valorNuevo().get("estado")) ? CambioPaciente.CANCELO : null;
            default -> null;
        };
    }
}
