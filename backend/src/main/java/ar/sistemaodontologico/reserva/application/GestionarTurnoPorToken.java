package ar.sistemaodontologico.reserva.application;

import ar.sistemaodontologico.agenda.application.AgendaParaReserva;
import ar.sistemaodontologico.agenda.application.LockDeAgenda;
import ar.sistemaodontologico.consultorio.domain.ConfiguracionDelConsultorio;
import ar.sistemaodontologico.consultorio.domain.DatosDelConsultorio;
import ar.sistemaodontologico.reserva.application.VistaDeTurno.ConsultorioVisible;
import ar.sistemaodontologico.reserva.application.VistaDeTurno.PacienteVisible;
import ar.sistemaodontologico.reserva.application.VistaDeTurno.TurnoVisible;
import ar.sistemaodontologico.reserva.domain.PlazoDeModificacion;
import ar.sistemaodontologico.reserva.domain.TokenDeGestion;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Ver, reprogramar y cancelar un turno con su token (US-031, US-032, design D3 y D8). El consultorio sale de la fila
 * del token. Las modificaciones vuelven a leer el token con el lock de agenda tomado: la segunda de dos operaciones
 * simultáneas ve lo que dejó la primera.
 */
@Service
public class GestionarTurnoPorToken {

    private static final String CANCELADO = "CANCELADO";

    private final TokenDeGestionRepositorio tokens;
    private final ConsultorioPublico publico;
    private final AgendaParaReserva agenda;
    private final LockDeAgenda lock;
    private final TransactionTemplate transaccion;

    GestionarTurnoPorToken(TokenDeGestionRepositorio tokens, ConsultorioPublico publico, AgendaParaReserva agenda,
            LockDeAgenda lock, PlatformTransactionManager transacciones) {
        this.tokens = tokens;
        this.publico = publico;
        this.agenda = agenda;
        this.lock = lock;
        this.transaccion = new TransactionTemplate(transacciones);
    }

    public VistaDeTurno ver(String crudo) {
        String hash = hash(crudo);
        TurnoPorToken turno = tokens.buscarConsultable(hash, publico.ahora())
                .orElseThrow(ErroresPublicos::tokenInvalido);
        return vista(turno, publico.porId(turno.consultorio()));
    }

    public HuecosPublicos huecos(String crudo, LocalDate desde, LocalDate hasta) {
        TurnoPorToken turno = modificable(hash(crudo));
        ConfiguracionDelConsultorio consultorio = publico.porId(turno.consultorio());
        ConsultorioPublico.exigirQueTomeTurnos(consultorio);
        return publico.huecos(consultorio, turno.duracionMin(), desde, hasta, Optional.of(turno.turno()));
    }

    public VistaDeTurno cancelar(String crudo, Optional<String> ip) {
        String hash = hash(crudo);
        TurnoPorToken leido = modificable(hash);
        publico.porId(leido.consultorio());
        transaccion.executeWithoutResult(estado -> {
            lock.tomar(leido.consultorio());
            TurnoPorToken turno = modificable(hash);
            ConfiguracionDelConsultorio consultorio = publico.porId(turno.consultorio());
            Instant ahora = publico.ahora();
            exigirPlazo(turno, consultorio, ahora);
            agenda.cancelar(turno.consultorio(), turno.turno(), turno.version(), turno.paciente(), ahora, ip);
            tokens.revocar(turno.consultorio(), turno.turno(), ahora);
        });
        return ver(crudo);
    }

    public VistaDeTurno reprogramar(String crudo, Instant nuevoInicio, Optional<String> ip) {
        String hash = hash(crudo);
        TurnoPorToken leido = modificable(hash);
        ConfiguracionDelConsultorio inicial = publico.porId(leido.consultorio());
        try {
            transaccion.executeWithoutResult(estado -> {
                lock.tomar(leido.consultorio());
                TurnoPorToken turno = modificable(hash);
                ConfiguracionDelConsultorio consultorio = publico.porId(turno.consultorio());
                ConsultorioPublico.exigirQueTomeTurnos(consultorio);
                Instant ahora = publico.ahora();
                exigirPlazo(turno, consultorio, ahora);
                if (!huecosDelDia(turno, consultorio, nuevoInicio, ahora).contains(nuevoInicio)) {
                    throw new HuecoTomado();
                }
                agenda.reprogramar(turno.consultorio(), turno.turno(), turno.version(), nuevoInicio, turno.paciente(),
                        ahora, ip);
            });
        } catch (RuntimeException e) {
            if (e instanceof HuecoTomado || AgendaParaReserva.esChoqueDeHorario(e)) {
                throw ErroresPublicos.huecoNoDisponible(huecosDelDia(leido, inicial, nuevoInicio, publico.ahora()));
            }
            throw e;
        }
        return ver(crudo);
    }

    private List<Instant> huecosDelDia(TurnoPorToken turno, ConfiguracionDelConsultorio consultorio, Instant instante,
            Instant ahora) {
        return publico.huecosDelDia(consultorio, turno.duracionMin(), instante, ahora, Optional.of(turno.turno()));
    }

    private TurnoPorToken modificable(String hash) {
        return tokens.buscarModificable(hash, publico.ahora()).orElseThrow(ErroresPublicos::tokenInvalido);
    }

    private static String hash(String crudo) {
        return TokenDeGestion.leer(crudo).orElseThrow(ErroresPublicos::tokenInvalido).hash();
    }

    private static void exigirPlazo(TurnoPorToken turno, ConfiguracionDelConsultorio consultorio, Instant ahora) {
        DatosDelConsultorio datos = consultorio.datos();
        if (!PlazoDeModificacion.permite(turno.inicio(), datos.reglas().anticipacionCancelacionMin(), ahora)) {
            throw ErroresPublicos.fueraDePlazo(datos.telefonoContacto());
        }
    }

    private VistaDeTurno vista(TurnoPorToken turno, ConfiguracionDelConsultorio consultorio) {
        DatosDelConsultorio datos = consultorio.datos();
        int anticipacion = datos.reglas().anticipacionCancelacionMin();
        boolean activo = !CANCELADO.equals(turno.estado());
        boolean puedeModificar = activo && PlazoDeModificacion.permite(turno.inicio(), anticipacion, publico.ahora());
        Instant limite = activo ? PlazoDeModificacion.limite(turno.inicio(), anticipacion) : null;
        return new VistaDeTurno(
                new TurnoVisible(turno.inicio(), turno.fin(), turno.prestacion(), turno.estado(), turno.canceladoPor()),
                new PacienteVisible(turno.nombre(), turno.apellido()),
                new ConsultorioVisible(datos.nombre(), datos.direccion(), datos.telefonoContacto(), datos.zonaHoraria()),
                puedeModificar, puedeModificar && ConsultorioPublico.tomaTurnos(consultorio), limite);
    }
}
