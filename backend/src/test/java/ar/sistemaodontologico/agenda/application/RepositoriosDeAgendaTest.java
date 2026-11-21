package ar.sistemaodontologico.agenda.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pruebas.agenda.AgendaDePrueba.lunesA;

import ar.sistemaodontologico.agenda.domain.Bloqueo;
import ar.sistemaodontologico.agenda.domain.BloqueoId;
import ar.sistemaodontologico.agenda.domain.Dni;
import ar.sistemaodontologico.agenda.domain.Duracion;
import ar.sistemaodontologico.agenda.domain.EstadoTurno;
import ar.sistemaodontologico.agenda.domain.Intervalo;
import ar.sistemaodontologico.agenda.domain.OrigenPaciente;
import ar.sistemaodontologico.agenda.domain.OrigenTurno;
import ar.sistemaodontologico.agenda.domain.Paciente;
import ar.sistemaodontologico.agenda.domain.PacienteId;
import ar.sistemaodontologico.agenda.domain.Turno;
import ar.sistemaodontologico.agenda.domain.TurnoId;
import ar.sistemaodontologico.compartido.IntegracionPostgresTest;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import pruebas.agenda.AgendaDePrueba;

/**
 * Repositorios JDBC de turnos y bloqueos (C-11, tarea 2.2; design D2). Toda consulta filtra por consultorio: lo de
 * otro consultorio no se encuentra ni se modifica (regla dura 6).
 */
class RepositoriosDeAgendaTest extends IntegracionPostgresTest {

    @Autowired
    private TurnoRepositorio turnos;

    @Autowired
    private BloqueoRepositorio bloqueos;

    @Autowired
    private PacienteRepositorio pacientes;

    @Autowired
    private JdbcTemplate jdbc;

    private AgendaDePrueba agenda;
    private ConsultorioId consultorio;
    private UUID prestacion;
    private UUID paciente;

    @BeforeEach
    void crearAgenda() {
        agenda = AgendaDePrueba.nueva(jdbc);
        consultorio = new ConsultorioId(agenda.consultorio());
        prestacion = agenda.prestacion("Consulta", 30, true);
        paciente = agenda.paciente("Ana", "Prueba");
    }

    // --- turnos ---

    @Test
    void unTurnoInsertadoSeLeeConSuVersion() {
        Turno nuevo = turnoNuevo("10:00");
        turnos.insertar(nuevo);

        TurnoGuardado guardado = turnos.buscar(consultorio, nuevo.id()).orElseThrow();

        assertThat(guardado.version()).isZero();
        assertThat(guardado.turno().inicio()).isEqualTo(lunesA("10:00"));
        assertThat(guardado.turno().fin()).isEqualTo(lunesA("10:30"));
        assertThat(guardado.turno().estado()).isEqualTo(EstadoTurno.RESERVADO);
        assertThat(guardado.turno().origen()).isEqualTo(OrigenTurno.MANUAL);
        assertThat(guardado.turno().pacienteId().valor()).isEqualTo(paciente);
    }

    @Test
    void unTurnoDeOtroConsultorioNoSeEncuentra() {
        UUID ajeno = otraAgendaConTurno();

        assertThat(turnos.buscar(consultorio, new TurnoId(ajeno))).isEmpty();
    }

    @Test
    void actualizarConLaVersionLeidaLaIncrementa() {
        Turno nuevo = turnoNuevo("10:00");
        turnos.insertar(nuevo);
        Turno turno = turnos.buscar(consultorio, nuevo.id()).orElseThrow().turno();
        turno.confirmar();
        turno.editarNota("Trae radiografía");

        long version = turnos.actualizar(turno, 0);

        TurnoGuardado guardado = turnos.buscar(consultorio, nuevo.id()).orElseThrow();
        assertThat(version).isEqualTo(1);
        assertThat(guardado.version()).isEqualTo(1);
        assertThat(guardado.turno().estado()).isEqualTo(EstadoTurno.CONFIRMADO);
        assertThat(guardado.turno().notaInterna()).isEqualTo("Trae radiografía");
    }

    @Test
    void actualizarConUnaVersionViejaFallaSinCambiar() {
        Turno nuevo = turnoNuevo("10:00");
        turnos.insertar(nuevo);
        Turno turno = turnos.buscar(consultorio, nuevo.id()).orElseThrow().turno();
        turno.ajustarDuracion(new Duracion(45));
        turnos.actualizar(turno, 0);
        turno.ajustarDuracion(new Duracion(60));

        assertThatThrownBy(() -> turnos.actualizar(turno, 0)).isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(turnos.buscar(consultorio, nuevo.id()).orElseThrow().turno().fin()).isEqualTo(lunesA("10:45"));
    }

    @Test
    void losTurnosQueIntersectanSonLosActivosDelConsultorio() {
        UUID activo = agenda.turno(paciente, prestacion, lunesA("10:00"), 30, "RESERVADO", false);
        UUID sobreturno = agenda.turno(paciente, prestacion, lunesA("10:10"), 20, "CONFIRMADO", true);
        agenda.turno(paciente, prestacion, lunesA("10:00"), 30, "CANCELADO", false);
        agenda.turno(paciente, prestacion, lunesA("10:30"), 30, "RESERVADO", false);
        otraAgendaConTurno();

        assertThat(turnos.listarActivosQueIntersectan(consultorio,
                new Intervalo(lunesA("09:45"), lunesA("10:30"))))
                .extracting(turno -> turno.id().valor())
                .containsExactlyInAnyOrder(activo, sobreturno);
    }

    // --- bloqueos ---

    @Test
    void unBloqueoInsertadoSeLeeYSeElimina() {
        Bloqueo bloqueo = new Bloqueo(new BloqueoId(UUID.randomUUID()),
                new Intervalo(lunesA("15:00"), lunesA("19:00")), "Curso");
        bloqueos.insertar(consultorio, bloqueo);

        assertThat(bloqueos.buscar(consultorio, bloqueo.id())).contains(bloqueo);
        assertThat(bloqueos.eliminar(consultorio, bloqueo.id())).isTrue();
        assertThat(bloqueos.buscar(consultorio, bloqueo.id())).isEmpty();
    }

    @Test
    void unBloqueoDeOtroConsultorioNoSeEncuentraNiSeElimina() {
        AgendaDePrueba otra = AgendaDePrueba.nueva(jdbc);
        BloqueoId ajeno = new BloqueoId(otra.bloqueo(lunesA("15:00"), lunesA("19:00"), "Curso"));

        assertThat(bloqueos.buscar(consultorio, ajeno)).isEmpty();
        assertThat(bloqueos.eliminar(consultorio, ajeno)).isFalse();
        assertThat(bloqueos.buscar(new ConsultorioId(otra.consultorio()), ajeno)).isPresent();
    }

    @Test
    void losBloqueosQueIntersectanSonLosDelConsultorio() {
        UUID tarde = agenda.bloqueo(lunesA("15:00"), lunesA("19:00"), null);
        agenda.bloqueo(lunesA("19:00"), lunesA("20:00"), null);
        AgendaDePrueba.nueva(jdbc).bloqueo(lunesA("15:00"), lunesA("19:00"), null);

        assertThat(bloqueos.listarQueIntersectan(consultorio, new Intervalo(lunesA("18:00"), lunesA("19:00"))))
                .extracting(bloqueo -> bloqueo.id().valor())
                .containsExactly(tarde);
    }

    // --- pacientes ---

    @Test
    void unPacienteDeOtroConsultorioNoSeEncuentra() {
        AgendaDePrueba otra = AgendaDePrueba.nueva(jdbc);
        UUID ajeno = otra.paciente("Otra", "Prueba");
        String dni = jdbc.queryForObject("SELECT dni FROM paciente WHERE id = ?", String.class, ajeno);

        assertThat(pacientes.buscar(consultorio, new PacienteId(ajeno))).isEmpty();
        assertThat(pacientes.buscarPorDniParaActualizar(consultorio, new Dni(dni))).isEmpty();
        assertThat(pacientes.buscar(new ConsultorioId(otra.consultorio()), new PacienteId(ajeno))).isPresent();
    }

    @Test
    void elMismoDniNoSeInsertaDosVecesEnElConsultorio() {
        String dni = AgendaDePrueba.dniNuevo();

        assertThat(pacientes.insertarSiNoExiste(consultorio, pacienteNuevo(dni))).isTrue();
        assertThat(pacientes.insertarSiNoExiste(consultorio, pacienteNuevo(dni))).isFalse();
        assertThat(pacientes.insertarSiNoExiste(new ConsultorioId(AgendaDePrueba.nueva(jdbc).consultorio()),
                pacienteNuevo(dni))).isTrue();
    }

    private static Paciente pacienteNuevo(String dni) {
        return Paciente.nuevo(new PacienteId(UUID.randomUUID()), "Ana", "Prueba", dni, "+5491100000001", null,
                OrigenPaciente.MANUAL, null);
    }

    private Turno turnoNuevo(String hora) {
        return Turno.nuevo(new TurnoId(UUID.randomUUID()), consultorio, new PacienteId(paciente), prestacion,
                lunesA(hora), new Duracion(30), OrigenTurno.MANUAL);
    }

    private UUID otraAgendaConTurno() {
        AgendaDePrueba otra = AgendaDePrueba.nueva(jdbc);
        return otra.turno(otra.paciente("Otra", "Prueba"), otra.prestacion("Consulta", 30, true), lunesA("10:00"), 30,
                "RESERVADO", false);
    }
}
