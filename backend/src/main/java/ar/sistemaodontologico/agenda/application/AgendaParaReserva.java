package ar.sistemaodontologico.agenda.application;

import static ar.sistemaodontologico.agenda.application.AgendaDelConsultorio.campos;

import ar.sistemaodontologico.agenda.domain.CalculadoraDeHuecos;
import ar.sistemaodontologico.agenda.domain.Consentimiento;
import ar.sistemaodontologico.agenda.domain.DisponibilidadSemanal;
import ar.sistemaodontologico.agenda.domain.Duracion;
import ar.sistemaodontologico.agenda.domain.Intervalo;
import ar.sistemaodontologico.agenda.domain.LimitesDeReserva;
import ar.sistemaodontologico.agenda.domain.OrigenPaciente;
import ar.sistemaodontologico.agenda.domain.Paciente;
import ar.sistemaodontologico.agenda.domain.PacienteId;
import ar.sistemaodontologico.agenda.domain.Turno;
import ar.sistemaodontologico.agenda.domain.TurnoId;
import ar.sistemaodontologico.agenda.domain.ValidadorDeSolapamiento;
import ar.sistemaodontologico.compartido.application.PublicadorDeAuditoria;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import ar.sistemaodontologico.compartido.domain.auditoria.CambioAuditado;
import ar.sistemaodontologico.compartido.domain.error.ConflictoDeNegocio;
import ar.sistemaodontologico.compartido.domain.error.ErrorDeNegocio;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lo que la reserva pública (C-15) le pide a la agenda, con tipos simples: {@code reserva} no depende del dominio de
 * agenda (C-15, design D1 y D2). Reusa los casos de uso de C-11. Las escrituras corren dentro de la transacción de
 * quien llama, que ya tomó el {@link LockDeAgenda}.
 */
@Component
public class AgendaParaReserva {

    public static final String CODIGO_LIMITE_TURNOS = "LIMITE_TURNOS";
    public static final String ACCION_CONSENTIMIENTO = "PACIENTE_CONSENTIMIENTO_ACTUALIZADO";

    private static final Set<String> CHOQUES_DE_HORARIO = Set.of(ValidadorDeSolapamiento.CODIGO_SOLAPADO,
            ValidadorDeSolapamiento.CODIGO_BLOQUEADO, ValidadorDeSolapamiento.CODIGO_FUERA_DE_DISPONIBILIDAD);
    private static final String EXCLUSION_DE_TURNOS = "ex_turno_sin_solapamiento";

    private final CrearTurno crear;
    private final CambiarEstadoTurno cambiarEstado;
    private final ReprogramarTurno reprogramar;
    private final RegistrarPaciente registrar;
    private final TurnoRepositorio turnos;
    private final BloqueoRepositorio bloqueos;
    private final PacienteRepositorio pacientes;
    private final ConsultaDeDisponibilidad disponibilidad;
    private final PublicadorDeAuditoria auditoria;

    AgendaParaReserva(CrearTurno crear, CambiarEstadoTurno cambiarEstado, ReprogramarTurno reprogramar,
            RegistrarPaciente registrar, TurnoRepositorio turnos, BloqueoRepositorio bloqueos,
            PacienteRepositorio pacientes, ConsultaDeDisponibilidad disponibilidad, PublicadorDeAuditoria auditoria) {
        this.crear = crear;
        this.cambiarEstado = cambiarEstado;
        this.reprogramar = reprogramar;
        this.registrar = registrar;
        this.turnos = turnos;
        this.bloqueos = bloqueos;
        this.pacientes = pacientes;
        this.disponibilidad = disponibilidad;
        this.auditoria = auditoria;
    }

    /** Reglas del consultorio con las que se calculan los huecos públicos (RN-RP-01). */
    public record ReglasDeHuecos(int duracionMin, int pasoMin, int anticipacionMin, int horizonteDias) {
    }

    /** Una fecha local del rango: si el consultorio atiende ese día de la semana y sus inicios libres. */
    public record DiaConHuecos(LocalDate fecha, boolean atiende, List<Instant> huecos) {
    }

    /** El turno creado por una reserva. */
    public record TurnoDeReserva(UUID id, Instant inicio, Instant fin, String estado) {
    }

    /** Pedido de una reserva ya validada. Los datos personales no aparecen en {@link #toString()}. */
    public record PedidoDeTurno(DatosDePaciente datos, UUID prestacion, Instant inicio, String versionPrivacidad,
            int maximoTurnosFuturos, Instant ahora, Optional<String> ip) {

        @Override
        public String toString() {
            return "PedidoDeTurno[prestacion=" + prestacion + ", inicio=" + inicio + "]";
        }
    }

    /** Valida y normaliza los datos antes de abrir la transacción: si son inválidos, lanza su error (400). */
    public void validarDatos(DatosDePaciente datos, String versionPrivacidad, Instant ahora) {
        Paciente.nuevo(new PacienteId(UUID.randomUUID()), datos.nombre(), datos.apellido(), datos.dni(), datos.telefono(),
                datos.email(), OrigenPaciente.LINK, new Consentimiento(ahora, versionPrivacidad));
    }

    /** Los días del rango con sus huecos; {@code sinTurno} no cuenta como ocupado (reprogramación, design D5). */
    public List<DiaConHuecos> huecos(ConsultorioId consultorio, LocalDate desde, LocalDate hasta, ReglasDeHuecos reglas,
            Instant ahora, Optional<UUID> sinTurno) {
        DisponibilidadSemanal grilla = disponibilidad.de(consultorio);
        ZoneId zona = grilla.zona();
        Intervalo rango = new Intervalo(desde.atStartOfDay(zona).toInstant(), hasta.plusDays(1).atStartOfDay(zona).toInstant());
        List<Turno> activos = turnos.listarActivosQueIntersectan(consultorio, rango).stream()
                .filter(turno -> sinTurno.map(id -> !id.equals(turno.id().valor())).orElse(true))
                .toList();
        List<Intervalo> ocupados = CalculadoraDeHuecos.ocupados(activos, bloqueos.listarQueIntersectan(consultorio, rango));
        List<Instant> libres = CalculadoraDeHuecos.calcular(grilla, desde, hasta, new Duracion(reglas.duracionMin()),
                reglas.pasoMin(), ocupados,
                Optional.of(new LimitesDeReserva(reglas.anticipacionMin(), reglas.horizonteDias(), ahora)));
        Map<LocalDate, List<Instant>> porFecha = new TreeMap<>();
        for (LocalDate fecha = desde; !fecha.isAfter(hasta); fecha = fecha.plusDays(1)) {
            porFecha.put(fecha, new ArrayList<>());
        }
        libres.forEach(hueco -> porFecha.get(hueco.atZone(zona).toLocalDate()).add(hueco));
        return porFecha.entrySet().stream()
                .map(dia -> new DiaConHuecos(dia.getKey(), !grilla.franjasDe(dia.getKey().getDayOfWeek()).isEmpty(),
                        List.copyOf(dia.getValue())))
                .toList();
    }

    /**
     * Pasos 6 a 8 de la reserva (design D6): busca o crea el paciente por DNI, aplica el límite de turnos futuros por
     * link (RN-RP-08), crea el turno {@code LINK} y deja la novedad para el odontólogo (DD-30).
     */
    @Transactional
    public TurnoDeReserva crearTurnoDeReserva(ConsultorioId consultorio, PedidoDeTurno pedido) {
        Consentimiento consentimiento = new Consentimiento(pedido.ahora(), pedido.versionPrivacidad());
        PacienteRegistrado registrado = registrar.registrar(pedido.datos(), OrigenPaciente.LINK, consentimiento,
                Autor.paciente(null, pedido.ip()));
        PacienteId paciente = registrado.paciente().id();
        Autor autor = Autor.paciente(paciente, pedido.ip());
        if (turnos.contarFuturosActivosPorLink(consultorio, paciente, pedido.ahora()) >= pedido.maximoTurnosFuturos()) {
            throw new ConflictoDeNegocio(CODIGO_LIMITE_TURNOS, "Se alcanzó el máximo de turnos próximos por link");
        }
        if (registrado.reutilizado()) {
            actualizarConsentimiento(consultorio, registrado.paciente(), consentimiento, autor);
        }
        TurnoGuardado guardado = crear.crear(new NuevoTurno(paciente, pedido.prestacion(), pedido.inicio(),
                Optional.empty(), false), autor);
        registrar.vincularTurnoDeOrigen(registrado, guardado.turno().id());
        turnos.marcarNovedadPaciente(consultorio, guardado.turno().id(), pedido.ahora());
        Turno turno = guardado.turno();
        return new TurnoDeReserva(turno.id().valor(), turno.inicio(), turno.fin(), turno.estado().name());
    }

    /** Cancela con {@code cancelado_por = PACIENTE} y deja la novedad (RN-RP-05, DD-30). */
    @Transactional
    public void cancelar(ConsultorioId consultorio, UUID turno, long version, UUID paciente, Instant ahora,
            Optional<String> ip) {
        TurnoId id = new TurnoId(turno);
        cambiarEstado.cancelar(id, version, null, Autor.paciente(new PacienteId(paciente), ip));
        turnos.marcarNovedadPaciente(consultorio, id, ahora);
    }

    /** Mueve el turno conservando id, duración y estado (design D8); al mismo horario no cambia nada. */
    @Transactional
    public void reprogramar(ConsultorioId consultorio, UUID turno, long version, Instant nuevoInicio, UUID paciente,
            Instant ahora, Optional<String> ip) {
        TurnoId id = new TurnoId(turno);
        TurnoGuardado guardado = reprogramar.reprogramar(id, version, nuevoInicio, Autor.paciente(new PacienteId(paciente), ip));
        if (guardado.version() != version) {
            turnos.marcarNovedadPaciente(consultorio, id, ahora);
        }
    }

    /** Si el error es que el horario ya no se puede usar: públicamente es {@code HUECO_NO_DISPONIBLE} (design D2). */
    public static boolean esChoqueDeHorario(RuntimeException error) {
        if (error instanceof ErrorDeNegocio negocio) {
            return CHOQUES_DE_HORARIO.contains(negocio.getCodigo());
        }
        return error instanceof DataIntegrityViolationException integridad
                && String.valueOf(integridad.getMostSpecificCause().getMessage()).contains(EXCLUSION_DE_TURNOS);
    }

    private void actualizarConsentimiento(ConsultorioId consultorio, Paciente paciente, Consentimiento nuevo, Autor autor) {
        pacientes.actualizarConsentimiento(consultorio, paciente.id(), nuevo);
        Consentimiento anterior = paciente.consentimiento();
        auditoria.publicar(new CambioAuditado(consultorio, autor.actor(), RegistrarPaciente.ENTIDAD, paciente.id().valor(),
                ACCION_CONSENTIMIENTO,
                campos("consentimiento_version", anterior == null ? null : anterior.version(),
                        "consentimiento_at", anterior == null ? null : anterior.aceptadoAt()),
                campos("consentimiento_version", nuevo.version(), "consentimiento_at", nuevo.aceptadoAt()),
                autor.ipONull()));
    }
}
