package ar.sistemaodontologico.agenda.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static pruebas.agenda.AgendaDePrueba.lunesA;
import static pruebas.agenda.Errores.assertCodigo;

import ar.sistemaodontologico.agenda.domain.CanceladoPor;
import ar.sistemaodontologico.agenda.domain.EstadoTurno;
import ar.sistemaodontologico.agenda.domain.PacienteId;
import ar.sistemaodontologico.agenda.domain.Turno;
import ar.sistemaodontologico.agenda.domain.TurnoId;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pruebas.agenda.AgendaDePrueba;

/**
 * Cambios sobre un turno existente (C-11, tareas 4.2 a 4.6; spec gestion-de-turnos, design D7 y D8): estado,
 * reprogramación, duración y nota, con la versión leída y auditoría solo cuando algo cambia.
 */
class ModificarTurnoTest extends CasoDeUsoDeAgendaTest {

    @Autowired
    private CambiarEstadoTurno estados;

    @Autowired
    private ReprogramarTurno reprogramar;

    @Autowired
    private AjustarDuracion duracion;

    @Autowired
    private EditarNota nota;

    @Autowired
    private TurnoRepositorio turnos;

    private UUID consulta;
    private PacienteId ana;
    private TurnoId diez;

    @BeforeEach
    void turnoDeLasDiez() {
        consulta = agenda.prestacion("Consulta", 30, true);
        ana = new PacienteId(agenda.paciente("Ana", "Prueba"));
        diez = new TurnoId(agenda.turno(ana.valor(), consulta, lunesA("10:00"), 30, "RESERVADO", false));
    }

    // --- estado (4.2) ---

    @Test
    void confirmarUnTurnoReservadoYAuditarlo() {
        TurnoGuardado confirmado = estados.confirmar(diez, 0, odontologo);

        assertThat(confirmado.turno().estado()).isEqualTo(EstadoTurno.CONFIRMADO);
        assertThat(confirmado.version()).isEqualTo(1);
        Evento evento = eventos().getFirst();
        assertThat(evento.accion()).isEqualTo(CambiarEstadoTurno.ACCION);
        assertThat(evento.actorTipo()).isEqualTo("ODONTOLOGO");
        assertThat(evento.anterior()).isEqualTo("{\"estado\": \"RESERVADO\", \"cancelado_por\": null}");
        assertThat(evento.nuevo()).isEqualTo("{\"estado\": \"CONFIRMADO\", \"cancelado_por\": null}");
    }

    @Test
    void elPacienteCancelaYElHorarioQuedaLibre() {
        estados.confirmar(diez, 0, odontologo);

        TurnoGuardado cancelado = estados.cancelar(diez, 1, "Viaje", Autor.paciente(ana, Optional.empty()));

        assertThat(cancelado.turno().estado()).isEqualTo(EstadoTurno.CANCELADO);
        assertThat(cancelado.turno().canceladoPor()).isEqualTo(CanceladoPor.PACIENTE);
        assertThat(turnos.listarActivosQueIntersectan(consultorio, cancelado.turno().intervalo())).isEmpty();
        Evento evento = eventos().getLast();
        assertThat(evento.actorTipo()).isEqualTo("PACIENTE");
        assertThat(evento.nuevo()).isEqualTo("{\"estado\": \"CANCELADO\", \"cancelado_por\": \"PACIENTE\"}")
                .doesNotContain("Viaje");
    }

    @Test
    void ausenteAntesDeTiempoEsTransicionInvalidaYDespuesSePuede() {
        reloj.avanzar(Duration.between(reloj.instant(), lunesA("09:00")));
        assertCodigo(() -> estados.marcarAusente(diez, 0, odontologo), "TRANSICION_INVALIDA");
        assertThat(turnos.buscar(consultorio, diez).orElseThrow().turno().estado()).isEqualTo(EstadoTurno.RESERVADO);

        reloj.avanzar(Duration.ofHours(1));
        assertThat(estados.marcarAusente(diez, 0, odontologo).turno().estado()).isEqualTo(EstadoTurno.AUSENTE);
    }

    @Test
    void unTurnoTerminalNoCambia() {
        estados.cancelar(diez, 0, null, odontologo);

        assertCodigo(() -> estados.confirmar(diez, 1, odontologo), "TRANSICION_INVALIDA");
        assertThat(acciones()).containsExactly(CambiarEstadoTurno.ACCION);
    }

    // --- reprogramar y duración (4.3) ---

    @Test
    void reprogramarAUnHuecoLibreConservaElIdYLaDuracion() {
        agenda.franja(1, "16:00", "18:00");

        TurnoGuardado movido = reprogramar.reprogramar(diez, 0, lunesA("16:00"), odontologo);

        assertThat(movido.turno().id()).isEqualTo(diez);
        assertThat(movido.turno().fin()).isEqualTo(lunesA("16:30"));
        Evento evento = eventos().getFirst();
        assertThat(evento.accion()).isEqualTo(ReprogramarTurno.ACCION);
        assertThat(evento.anterior())
                .isEqualTo("{\"fin\": \"2026-11-23T13:30:00Z\", \"inicio\": \"2026-11-23T13:00:00Z\"}");
        assertThat(evento.nuevo())
                .isEqualTo("{\"fin\": \"2026-11-23T19:30:00Z\", \"inicio\": \"2026-11-23T19:00:00Z\"}");
    }

    @Test
    void correrseDentroDeSuPropioHorario() {
        assertThat(reprogramar.reprogramar(diez, 0, lunesA("10:15"), odontologo).turno().fin())
                .isEqualTo(lunesA("10:45"));
    }

    @Test
    void reprogramarAlMismoInicioNoAudita() {
        TurnoGuardado igual = reprogramar.reprogramar(diez, 0, lunesA("10:00"), odontologo);

        assertThat(igual.version()).isZero();
        assertThat(acciones()).isEmpty();
    }

    @Test
    void reprogramarSobreOtroTurnoNoCambiaNada() {
        agenda.turno(ana.valor(), consulta, lunesA("11:00"), 30, "RESERVADO", false);

        assertCodigo(() -> reprogramar.reprogramar(diez, 0, lunesA("11:15"), odontologo), "TURNO_SOLAPADO");
        assertThat(turnos.buscar(consultorio, diez).orElseThrow().turno().inicio()).isEqualTo(lunesA("10:00"));
    }

    @Test
    void elPacienteReprogramaYQuedaAuditado() {
        reprogramar.reprogramar(diez, 0, lunesA("11:00"), Autor.paciente(ana, Optional.empty()));

        assertThat(eventos().getFirst().actorTipo()).isEqualTo("PACIENTE");
    }

    @Test
    void alargarDentroDeUnHuecoLibre() {
        TurnoGuardado alargado = duracion.ajustar(diez, 0, 45, odontologo);

        assertThat(alargado.turno().fin()).isEqualTo(lunesA("10:45"));
        Evento evento = eventos().getFirst();
        assertThat(evento.accion()).isEqualTo(AjustarDuracion.ACCION);
        assertThat(evento.anterior()).isEqualTo("{\"fin\": \"2026-11-23T13:30:00Z\", \"duracion_min\": 30}");
        assertThat(evento.nuevo()).isEqualTo("{\"fin\": \"2026-11-23T13:45:00Z\", \"duracion_min\": 45}");
    }

    @Test
    void alargarSobreOtroTurnoOUnBloqueoNoCambiaLaDuracion() {
        agenda.turno(ana.valor(), consulta, lunesA("10:30"), 30, "RESERVADO", false);
        assertCodigo(() -> duracion.ajustar(diez, 0, 45, odontologo), "TURNO_SOLAPADO");

        TurnoId otro = new TurnoId(agenda.turno(ana.valor(), consulta, lunesA("11:00"), 30, "RESERVADO", false));
        agenda.bloqueo(lunesA("11:40"), lunesA("12:00"), null);
        assertCodigo(() -> duracion.ajustar(otro, 0, 45, odontologo), "HORARIO_BLOQUEADO");

        assertThat(turnos.buscar(consultorio, diez).orElseThrow().turno().duracion().minutos()).isEqualTo(30);
        assertThat(turnos.buscar(consultorio, otro).orElseThrow().turno().duracion().minutos()).isEqualTo(30);
    }

    @Test
    void duracionInvalidaYSoloDelOdontologo() {
        assertCodigo(() -> duracion.ajustar(diez, 0, 42, odontologo), "DURACION_INVALIDA");
        assertCodigo(() -> duracion.ajustar(diez, 0, 45, Autor.paciente(ana, Optional.empty())),
                "ACCION_NO_PERMITIDA");
    }

    // --- nota (4.4) ---

    @Test
    void notaGuardadaYAuditadaCompleta() {
        TurnoGuardado anotado = nota.editar(diez, 0, "Trae radiografía", odontologo);

        assertThat(anotado.turno().notaInterna()).isEqualTo("Trae radiografía");
        Evento evento = eventos().getFirst();
        assertThat(evento.accion()).isEqualTo(EditarNota.ACCION);
        assertThat(evento.anterior()).isEqualTo("{\"nota_interna\": null}");
        assertThat(evento.nuevo()).isEqualTo("{\"nota_interna\": \"Trae radiografía\"}");
    }

    @Test
    void laMismaNotaNoAudita() {
        nota.editar(diez, 0, "Trae radiografía", odontologo);

        TurnoGuardado igual = nota.editar(diez, 1, " Trae radiografía ", odontologo);

        assertThat(igual.version()).isEqualTo(1);
        assertThat(acciones()).containsExactly(EditarNota.ACCION);
    }

    @Test
    void notaDemasiadoLargaOPedidaPorElPaciente() {
        nota.editar(diez, 0, "Trae radiografía", odontologo);

        assertCodigo(() -> nota.editar(diez, 1, "a".repeat(501), odontologo), "VALIDACION");
        assertCodigo(() -> nota.editar(diez, 1, "Otra", Autor.paciente(ana, Optional.empty())),
                "ACCION_NO_PERMITIDA");
        assertThat(turnos.buscar(consultorio, diez).orElseThrow().turno().notaInterna()).isEqualTo("Trae radiografía");
    }

    // --- concurrencia y aislamiento (4.6) ---

    @Test
    void unaVersionViejaEsConflictoYNoCambia() {
        estados.confirmar(diez, 0, odontologo);

        assertCodigoDeConcurrencia(() -> nota.editar(diez, 0, "Trae radiografía", odontologo));
        assertCodigoDeConcurrencia(() -> estados.cancelar(diez, 0, null, odontologo));
        Turno turno = turnos.buscar(consultorio, diez).orElseThrow().turno();
        assertThat(turno.estado()).isEqualTo(EstadoTurno.CONFIRMADO);
        assertThat(turno.notaInterna()).isNull();
    }

    @Test
    void unTurnoDeOtroConsultorioNoSeEncuentraNiCambia() {
        AgendaDePrueba otra = AgendaDePrueba.nueva(jdbc);
        TurnoId ajeno = new TurnoId(otra.turno(otra.paciente("Otra", "Prueba"), otra.prestacion("Consulta", 30, true),
                lunesA("10:00"), 30, "RESERVADO", false));

        assertCodigo(() -> estados.confirmar(ajeno, 0, odontologo), "RECURSO_NO_ENCONTRADO");
        assertCodigo(() -> reprogramar.reprogramar(ajeno, 0, lunesA("11:00"), odontologo), "RECURSO_NO_ENCONTRADO");
        assertCodigo(() -> duracion.ajustar(ajeno, 0, 45, odontologo), "RECURSO_NO_ENCONTRADO");
        assertCodigo(() -> nota.editar(ajeno, 0, "Nota", odontologo), "RECURSO_NO_ENCONTRADO");
        when(tenant.actual()).thenReturn(new ConsultorioId(otra.consultorio()));
        assertThat(turnos.buscar(new ConsultorioId(otra.consultorio()), ajeno).orElseThrow().version()).isZero();
    }

    private static void assertCodigoDeConcurrencia(org.assertj.core.api.ThrowableAssert.ThrowingCallable accion) {
        org.assertj.core.api.Assertions.assertThatThrownBy(accion)
                .isInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class);
    }
}
