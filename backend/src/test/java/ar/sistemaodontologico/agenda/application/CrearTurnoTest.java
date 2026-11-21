package ar.sistemaodontologico.agenda.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static pruebas.agenda.AgendaDePrueba.lunesA;
import static pruebas.agenda.Errores.assertCodigo;
import static pruebas.agenda.Errores.propiedadesDelError;

import ar.sistemaodontologico.agenda.domain.EstadoTurno;
import ar.sistemaodontologico.agenda.domain.OrigenTurno;
import ar.sistemaodontologico.agenda.domain.PacienteId;
import ar.sistemaodontologico.agenda.domain.Turno;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pruebas.agenda.AgendaDePrueba;

/**
 * {@link CrearTurno} (C-11, tareas 4.1 y 4.5; spec gestion-de-turnos, design D4, D5 y D8). La agenda de prueba
 * atiende de lunes a viernes de 09:00 a 13:00 en Buenos Aires.
 */
class CrearTurnoTest extends CasoDeUsoDeAgendaTest {

    @Autowired
    private CrearTurno crear;

    @Autowired
    private TurnoRepositorio turnos;

    private UUID consulta;
    private PacienteId ana;

    @BeforeEach
    void prestacionYPaciente() {
        consulta = agenda.prestacion("Consulta", 30, true);
        ana = new PacienteId(agenda.paciente("Ana", "Prueba"));
    }

    @Test
    void turnoManualEnUnHuecoLibre() {
        TurnoGuardado guardado = crear.crear(pedido("10:00"), odontologo);

        Turno turno = guardado.turno();
        assertThat(guardado.version()).isZero();
        assertThat(turno.estado()).isEqualTo(EstadoTurno.RESERVADO);
        assertThat(turno.origen()).isEqualTo(OrigenTurno.MANUAL);
        assertThat(turno.duracion().minutos()).isEqualTo(30);
        assertThat(turno.fin()).isEqualTo(lunesA("10:30"));
        assertThat(turnos.buscar(consultorio, turno.id())).isPresent();
    }

    @Test
    void elOdontologoPuedePedirOtraDuracion() {
        TurnoGuardado guardado = crear.crear(new NuevoTurno(ana, consulta, lunesA("10:00"), Optional.of(45), false),
                odontologo);

        assertThat(guardado.turno().fin()).isEqualTo(lunesA("10:45"));
    }

    @Test
    void elPacienteCreaConOrigenLinkYLaDuracionDeLaPrestacion() {
        TurnoGuardado guardado = crear.crear(pedido("10:00"), Autor.paciente(ana, Optional.empty()));

        assertThat(guardado.turno().origen()).isEqualTo(OrigenTurno.LINK);
        assertThat(guardado.turno().duracion().minutos()).isEqualTo(30);
        assertThat(eventos().getFirst().actorTipo()).isEqualTo("PACIENTE");
        assertThat(eventos().getFirst().actorId()).isEqualTo(ana.valor());
    }

    @Test
    void elPacienteNoEligeLaDuracionNiPideSobreturnos() {
        Autor paciente = Autor.paciente(ana, Optional.empty());

        assertCodigo(() -> crear.crear(new NuevoTurno(ana, consulta, lunesA("10:00"), Optional.of(45), false),
                paciente), "ACCION_NO_PERMITIDA");
        assertCodigo(() -> crear.crear(new NuevoTurno(ana, consulta, lunesA("10:00"), Optional.empty(), true),
                paciente), "ACCION_NO_PERMITIDA");
        assertThat(acciones()).isEmpty();
    }

    @Test
    void unTurnoQueSeSolapaSeRechazaConElTurnoEnConflicto() {
        UUID ocupado = agenda.turno(ana.valor(), consulta, lunesA("10:00"), 30, "RESERVADO", false);

        assertThat(propiedadesDelError(() -> crear.crear(pedido("10:15"), odontologo), "TURNO_SOLAPADO"))
                .containsEntry("turnosEnConflicto", List.of(ocupado.toString()));
        assertThat(acciones()).isEmpty();
    }

    @Test
    void turnosContiguos() {
        agenda.turno(ana.valor(), consulta, lunesA("10:00"), 30, "RESERVADO", false);

        assertThat(crear.crear(pedido("10:30"), odontologo).turno().fin()).isEqualTo(lunesA("11:00"));
    }

    @Test
    void fueraDeLaDisponibilidadYDentroDeUnBloqueo() {
        assertCodigo(() -> crear.crear(pedido("13:00"), odontologo), "FUERA_DE_DISPONIBILIDAD");
        agenda.bloqueo(lunesA("11:00"), lunesA("12:00"), "Curso");
        assertCodigo(() -> crear.crear(pedido("11:00"), odontologo), "HORARIO_BLOQUEADO");
    }

    @Test
    void sobreturnoSobreUnTurnoOcupadoPeroNoEnUnBloqueo() {
        agenda.turno(ana.valor(), consulta, lunesA("10:00"), 30, "RESERVADO", false);
        agenda.bloqueo(lunesA("14:00"), lunesA("18:00"), "Curso");

        TurnoGuardado sobreturno = crear.crear(new NuevoTurno(ana, consulta, lunesA("10:00"), Optional.of(20), true),
                odontologo);

        assertThat(sobreturno.turno().esSobreturno()).isTrue();
        assertThat(sobreturno.turno().origen()).isEqualTo(OrigenTurno.MANUAL);
        assertCodigo(() -> crear.crear(new NuevoTurno(ana, consulta, lunesA("15:00"), Optional.empty(), true),
                odontologo), "HORARIO_BLOQUEADO");
    }

    @Test
    void prestacionInactivaAjenaOInexistente() {
        UUID blanqueamiento = agenda.prestacion("Blanqueamiento", 60, false);
        UUID ajena = AgendaDePrueba.nueva(jdbc).prestacion("Consulta", 30, true);

        assertCodigo(() -> crear.crear(new NuevoTurno(ana, blanqueamiento, lunesA("10:00"), Optional.empty(), false),
                odontologo), "PRESTACION_INACTIVA");
        assertCodigo(() -> crear.crear(new NuevoTurno(ana, ajena, lunesA("10:00"), Optional.empty(), false),
                odontologo), "RECURSO_NO_ENCONTRADO");
        assertCodigo(() -> crear.crear(new NuevoTurno(ana, UUID.randomUUID(), lunesA("10:00"), Optional.empty(),
                false), odontologo), "RECURSO_NO_ENCONTRADO");
    }

    @Test
    void unPacienteDeOtroConsultorioNoSeEncuentra() {
        PacienteId ajeno = new PacienteId(AgendaDePrueba.nueva(jdbc).paciente("Otra", "Prueba"));

        assertCodigo(() -> crear.crear(new NuevoTurno(ajeno, consulta, lunesA("10:00"), Optional.empty(), false),
                odontologo), "RECURSO_NO_ENCONTRADO");
    }

    @Test
    void duracionYHorarioInvalidos() {
        assertCodigo(() -> crear.crear(new NuevoTurno(ana, consulta, lunesA("10:00"), Optional.of(42), false),
                odontologo), "DURACION_INVALIDA");
        assertCodigo(() -> crear.crear(new NuevoTurno(ana, consulta, lunesA("10:00").plusSeconds(60),
                Optional.empty(), false), odontologo), "HORARIO_INVALIDO");
    }

    @Test
    void elEventoTurnoCreadoNoTieneDatosDelPaciente() {
        TurnoGuardado guardado = crear.crear(pedido("10:00"), odontologo);

        Evento evento = eventos().getFirst();
        assertThat(evento.accion()).isEqualTo(CrearTurno.ACCION);
        assertThat(evento.entidad()).isEqualTo("turno");
        assertThat(evento.entidadId()).isEqualTo(guardado.turno().id().valor());
        assertThat(evento.anterior()).isNull();
        assertThat(evento.nuevo())
                .contains("\"paciente_id\": \"" + ana.valor() + "\"")
                .contains("\"inicio\": \"2026-11-23T13:00:00Z\"").contains("\"fin\": \"2026-11-23T13:30:00Z\"")
                .contains("\"duracion_min\": 30").contains("\"estado\": \"RESERVADO\"")
                .contains("\"origen\": \"MANUAL\"").contains("\"es_sobreturno\": false")
                .contains("\"tipo_prestacion_id\": \"" + consulta + "\"")
                .doesNotContain("Ana").doesNotContain("Prueba").doesNotContain("+549");
    }

    @Test
    void siFallaLaAuditoriaElTurnoNoQueda() {
        doThrow(new IllegalStateException("falla simulada")).when(auditoria).publicar(any());

        assertThatThrownBy(() -> crear.crear(pedido("10:00"), odontologo)).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM turno WHERE consultorio_id = ?", Integer.class,
                consultorio.valor())).isZero();
    }

    private NuevoTurno pedido(String hora) {
        return new NuevoTurno(ana, consulta, lunesA(hora), Optional.empty(), false);
    }
}
