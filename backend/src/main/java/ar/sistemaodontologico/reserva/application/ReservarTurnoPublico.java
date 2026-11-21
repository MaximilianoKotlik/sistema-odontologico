package ar.sistemaodontologico.reserva.application;

import ar.sistemaodontologico.agenda.application.AgendaParaReserva;
import ar.sistemaodontologico.agenda.application.AgendaParaReserva.PedidoDeTurno;
import ar.sistemaodontologico.agenda.application.AgendaParaReserva.TurnoDeReserva;
import ar.sistemaodontologico.agenda.application.DatosDePaciente;
import ar.sistemaodontologico.agenda.application.LockDeAgenda;
import ar.sistemaodontologico.compartido.application.GeneradorDeSecretos;
import ar.sistemaodontologico.compartido.domain.GeneradorDeIds;
import ar.sistemaodontologico.consultorio.application.PrestacionRepositorio;
import ar.sistemaodontologico.consultorio.domain.ConfiguracionDelConsultorio;
import ar.sistemaodontologico.consultorio.domain.TipoDePrestacion;
import ar.sistemaodontologico.reserva.application.ReservaCreada.TurnoReservado;
import ar.sistemaodontologico.reserva.domain.TokenDeGestion;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Reserva sin cuenta, con el orden fijo de design D6. El lock de agenda hace correctos el hueco, la deduplicación y el
 * límite ante reservas simultáneas. Los huecos del 409 se calculan después del rollback, por eso la transacción se
 * abre acá y no con {@code @Transactional}.
 */
@Service
public class ReservarTurnoPublico {

    /** RN-RP-08. */
    static final int MAXIMO_TURNOS_FUTUROS = 2;

    private final ConsultorioPublico publico;
    private final PrestacionRepositorio prestaciones;
    private final AgendaParaReserva agenda;
    private final LockDeAgenda lock;
    private final TokenDeGestionRepositorio tokens;
    private final GeneradorDeSecretos secretos;
    private final GeneradorDeIds ids;
    private final ApplicationEventPublisher eventos;
    private final TransactionTemplate transaccion;
    private final String versionPrivacidad;

    ReservarTurnoPublico(ConsultorioPublico publico, PrestacionRepositorio prestaciones, AgendaParaReserva agenda,
            LockDeAgenda lock, TokenDeGestionRepositorio tokens, GeneradorDeSecretos secretos, GeneradorDeIds ids,
            ApplicationEventPublisher eventos, PlatformTransactionManager transacciones,
            @Value("${app.privacidad.version-vigente}") String versionPrivacidad) {
        this.publico = publico;
        this.prestaciones = prestaciones;
        this.agenda = agenda;
        this.lock = lock;
        this.tokens = tokens;
        this.secretos = secretos;
        this.ids = ids;
        this.eventos = eventos;
        this.transaccion = new TransactionTemplate(transacciones);
        this.versionPrivacidad = versionPrivacidad;
    }

    /** Pedido validado en formato por la capa web. Los datos personales no aparecen en {@link #toString()}. */
    public record PedidoDeReserva(UUID prestacionId, Instant inicio, DatosDePaciente datos) {

        @Override
        public String toString() {
            return "PedidoDeReserva[prestacion=" + prestacionId + ", inicio=" + inicio + "]";
        }
    }

    public ReservaCreada reservar(String slug, PedidoDeReserva pedido, Optional<String> ip) {
        agenda.validarDatos(pedido.datos(), versionPrivacidad, publico.ahora());
        ConfiguracionDelConsultorio consultorio = publico.porSlug(slug);
        publico.exigirQueTomeTurnos(consultorio);
        TipoDePrestacion prestacion = ConsultarHuecosPublicos.prestacionOfrecida(prestaciones, consultorio,
                pedido.prestacionId());
        try {
            return transaccion.execute(estado -> crear(consultorio, prestacion, pedido, ip));
        } catch (HuecoTomado | org.springframework.dao.DataIntegrityViolationException e) {
            throw traducir(e, consultorio, prestacion, pedido);
        } catch (RuntimeException e) {
            if (AgendaParaReserva.esChoqueDeHorario(e)) {
                throw traducir(e, consultorio, prestacion, pedido);
            }
            throw e;
        }
    }

    private ReservaCreada crear(ConfiguracionDelConsultorio leida, TipoDePrestacion prestacion, PedidoDeReserva pedido,
            Optional<String> ip) {
        lock.tomar(leida.id());
        // Con el lock tomado: la configuración y la grilla son las vigentes (tarea 6.5a).
        ConfiguracionDelConsultorio consultorio = publico.porId(leida.id());
        publico.exigirQueTomeTurnos(consultorio);
        Instant ahora = publico.ahora();
        if (!publico.huecosDelDia(consultorio, prestacion.duracionMin(), pedido.inicio(), ahora, Optional.empty())
                .contains(pedido.inicio())) {
            throw new HuecoTomado();
        }
        TurnoDeReserva turno = agenda.crearTurnoDeReserva(consultorio.id(), new PedidoDeTurno(pedido.datos(),
                prestacion.id(), pedido.inicio(), versionPrivacidad, MAXIMO_TURNOS_FUTUROS, ahora, ip));
        TokenDeGestion token = TokenDeGestion.emitido(secretos.nuevo());
        tokens.insertar(consultorio.id(), ids.nuevo(), turno.id(), token.hash());
        String email = pedido.datos().email();
        if (email != null && !email.isBlank()) {
            eventos.publishEvent(new ReservaConfirmada(email.strip(), consultorio.datos().nombre(), turno.inicio(),
                    ConsultorioPublico.zona(consultorio), token.valor()));
        }
        return new ReservaCreada(new TurnoReservado(turno.id(), turno.inicio(), turno.fin(), prestacion.nombre(),
                turno.estado()), "/t/" + token.valor());
    }

    private RuntimeException traducir(RuntimeException error, ConfiguracionDelConsultorio consultorio,
            TipoDePrestacion prestacion, PedidoDeReserva pedido) {
        if (error instanceof HuecoTomado || AgendaParaReserva.esChoqueDeHorario(error)) {
            return ErroresPublicos.huecoNoDisponible(publico.huecosDelDia(consultorio, prestacion.duracionMin(),
                    pedido.inicio(), publico.ahora(), Optional.empty()));
        }
        return error;
    }
}
