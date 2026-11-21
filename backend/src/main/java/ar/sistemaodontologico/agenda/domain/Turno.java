package ar.sistemaodontologico.agenda.domain;

import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import ar.sistemaodontologico.compartido.domain.error.ValidacionDeNegocio;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Turno de la agenda (spec turnos, design D3). El fin siempre se calcula como {@code inicio + duracion} (RN-AG-12) y
 * no se ingresa. Los comandos validan los invariantes propios del turno (estado, grilla y largos); el solapamiento
 * con otros turnos lo valida {@link ValidadorDeSolapamiento}. Dos turnos son iguales si tienen el mismo id.
 */
public final class Turno {

    public static final int LARGO_MAXIMO_NOTA = 500;
    public static final int LARGO_MAXIMO_MOTIVO = 200;

    private final TurnoId id;
    private final ConsultorioId consultorioId;
    private final PacienteId pacienteId;
    private final UUID tipoPrestacionId;
    private final boolean esSobreturno;
    private final OrigenTurno origen;
    private Instant inicio;
    private Duracion duracion;
    private EstadoTurno estado;
    private CanceladoPor canceladoPor;
    private String motivoCancelacion;
    private String notaInterna;

    private Turno(TurnoId id, ConsultorioId consultorioId, PacienteId pacienteId, UUID tipoPrestacionId,
            Instant inicio, Duracion duracion, EstadoTurno estado, boolean esSobreturno, OrigenTurno origen,
            CanceladoPor canceladoPor, String motivoCancelacion, String notaInterna) {
        this.id = Objects.requireNonNull(id, "id");
        this.consultorioId = Objects.requireNonNull(consultorioId, "consultorioId");
        this.pacienteId = Objects.requireNonNull(pacienteId, "pacienteId");
        this.tipoPrestacionId = Objects.requireNonNull(tipoPrestacionId, "tipoPrestacionId");
        this.inicio = enLaGrilla(inicio);
        this.duracion = Objects.requireNonNull(duracion, "duracion");
        this.estado = Objects.requireNonNull(estado, "estado");
        this.esSobreturno = esSobreturno;
        this.origen = Objects.requireNonNull(origen, "origen");
        // RN-AG-04: el sobreturno lo crea solo el odontólogo.
        if (esSobreturno && origen != OrigenTurno.MANUAL) {
            throw Textos.invalido();
        }
        // Quién canceló existe solo en un turno cancelado, y el motivo también (ck_turno_cancelacion).
        if ((estado == EstadoTurno.CANCELADO) != (canceladoPor != null)
                || (motivoCancelacion != null && estado != EstadoTurno.CANCELADO)) {
            throw Textos.invalido();
        }
        this.canceladoPor = canceladoPor;
        this.motivoCancelacion = Textos.opcional(motivoCancelacion, LARGO_MAXIMO_MOTIVO);
        this.notaInterna = Textos.opcional(notaInterna, LARGO_MAXIMO_NOTA);
    }

    /**
     * Un turno nuevo, {@code RESERVADO}. La duración es una copia de la de su prestación en este momento (RN-AG-02):
     * cambiar después la prestación no lo altera.
     */
    public static Turno nuevo(TurnoId id, ConsultorioId consultorio, PacienteId paciente, UUID tipoPrestacion,
            Instant inicio, Duracion duracionDeLaPrestacion, OrigenTurno origen) {
        return new Turno(id, consultorio, paciente, tipoPrestacion, inicio, duracionDeLaPrestacion,
                EstadoTurno.RESERVADO, false, origen, null, null, null);
    }

    /** Un sobreturno nuevo: siempre {@code MANUAL} (RN-AG-04) y con la marca para toda su vida (DD-10). */
    public static Turno nuevoSobreturno(TurnoId id, ConsultorioId consultorio, PacienteId paciente,
            UUID tipoPrestacion, Instant inicio, Duracion duracion) {
        return new Turno(id, consultorio, paciente, tipoPrestacion, inicio, duracion, EstadoTurno.RESERVADO, true,
                OrigenTurno.MANUAL, null, null, null);
    }

    /** Reconstruye un turno guardado (C-11), validando los mismos invariantes que la base. */
    public static Turno restaurar(TurnoId id, ConsultorioId consultorio, PacienteId paciente, UUID tipoPrestacion,
            Instant inicio, Duracion duracion, EstadoTurno estado, boolean esSobreturno, OrigenTurno origen,
            CanceladoPor canceladoPor, String motivoCancelacion, String notaInterna) {
        return new Turno(id, consultorio, paciente, tipoPrestacion, inicio, duracion, estado, esSobreturno, origen,
                canceladoPor, motivoCancelacion, notaInterna);
    }

    public void confirmar() {
        estado = MaquinaDeEstadosTurno.siguiente(estado, AccionTurno.CONFIRMAR, inicio, inicio);
    }

    public void cancelar(CanceladoPor por, String motivo) {
        Objects.requireNonNull(por, "por");
        String motivoValido = Textos.opcional(motivo, LARGO_MAXIMO_MOTIVO);
        estado = MaquinaDeEstadosTurno.siguiente(estado, AccionTurno.CANCELAR, inicio, inicio);
        canceladoPor = por;
        motivoCancelacion = motivoValido;
    }

    /** Solo cuando el turno ya empezó: {@code ahora} lo pasa quien llama, con el reloj del sistema. */
    public void marcarAusente(Instant ahora) {
        estado = MaquinaDeEstadosTurno.siguiente(estado, AccionTurno.MARCAR_AUSENTE, inicio, ahora);
    }

    /** Mueve el turno conservando su id y su duración (RN-AG-11, RN-AG-12). */
    public void reprogramar(Instant nuevoInicio) {
        MaquinaDeEstadosTurno.siguiente(estado, AccionTurno.REPROGRAMAR, inicio, inicio);
        inicio = enLaGrilla(nuevoInicio);
    }

    public void ajustarDuracion(Duracion nuevaDuracion) {
        Objects.requireNonNull(nuevaDuracion, "nuevaDuracion");
        MaquinaDeEstadosTurno.siguiente(estado, AccionTurno.AJUSTAR_DURACION, inicio, inicio);
        duracion = nuevaDuracion;
    }

    public void editarNota(String nota) {
        notaInterna = Textos.opcional(nota, LARGO_MAXIMO_NOTA);
    }

    public Instant fin() {
        return inicio.plus(duracion.comoDuration());
    }

    public Intervalo intervalo() {
        return new Intervalo(inicio, fin());
    }

    public boolean estaActivo() {
        return estado.esActivo();
    }

    public TurnoId id() {
        return id;
    }

    public ConsultorioId consultorioId() {
        return consultorioId;
    }

    public PacienteId pacienteId() {
        return pacienteId;
    }

    public UUID tipoPrestacionId() {
        return tipoPrestacionId;
    }

    public Instant inicio() {
        return inicio;
    }

    public Duracion duracion() {
        return duracion;
    }

    public EstadoTurno estado() {
        return estado;
    }

    public boolean esSobreturno() {
        return esSobreturno;
    }

    public OrigenTurno origen() {
        return origen;
    }

    public CanceladoPor canceladoPor() {
        return canceladoPor;
    }

    public String motivoCancelacion() {
        return motivoCancelacion;
    }

    public String notaInterna() {
        return notaInterna;
    }

    @Override
    public boolean equals(Object otro) {
        return otro instanceof Turno turno && id.equals(turno.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    /** Sin la nota ni el motivo: pueden tener datos del paciente (RN-GL-01). */
    @Override
    public String toString() {
        return "Turno[id=" + id.valor() + ", estado=" + estado + ", sobreturno=" + esSobreturno + "]";
    }

    private static Instant enLaGrilla(Instant instante) {
        Objects.requireNonNull(instante, "inicio");
        if (!Intervalo.estaEnLaGrilla(instante)) {
            throw new ValidacionDeNegocio(Intervalo.CODIGO_HORARIO_INVALIDO,
                    "El turno tiene que empezar en un múltiplo de 5 minutos");
        }
        return instante;
    }
}
