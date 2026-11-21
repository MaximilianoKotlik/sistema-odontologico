package ar.sistemaodontologico.agenda.application;

import static org.assertj.core.api.Assertions.assertThat;
import static pruebas.agenda.AgendaDePrueba.lunesA;
import static pruebas.agenda.Errores.assertCodigo;
import static pruebas.agenda.Errores.propiedadesDelError;

import ar.sistemaodontologico.agenda.domain.Bloqueo;
import ar.sistemaodontologico.agenda.domain.BloqueoId;
import ar.sistemaodontologico.agenda.domain.PacienteId;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pruebas.agenda.AgendaDePrueba;

/** {@link CrearBloqueo} y {@link EliminarBloqueo} (C-11, tarea 5.1; spec gestion-de-bloqueos, design D8). */
class BloqueosTest extends CasoDeUsoDeAgendaTest {

    /** Jueves 2026-11-26 en Buenos Aires. */
    private static final Instant JUEVES = lunesA("00:00").plusSeconds(3 * 24 * 3600L);

    @Autowired
    private CrearBloqueo crear;

    @Autowired
    private EliminarBloqueo eliminar;

    @Autowired
    private BloqueoRepositorio bloqueos;

    private UUID consulta;
    private UUID ana;

    @BeforeEach
    void prestacionYPaciente() {
        consulta = agenda.prestacion("Consulta", 30, true);
        ana = agenda.paciente("Ana", "Prueba");
    }

    @Test
    void unRangoSinTurnosActivosSeBloqueaYSeAudita() {
        Bloqueo bloqueo = crear.crear(jueves(15), jueves(19), "Curso", odontologo);

        assertThat(bloqueos.buscar(consultorio, bloqueo.id())).contains(bloqueo);
        Evento evento = eventos().getFirst();
        assertThat(evento.accion()).isEqualTo(CrearBloqueo.ACCION);
        assertThat(evento.entidad()).isEqualTo("bloqueo");
        assertThat(evento.anterior()).isNull();
        assertThat(evento.nuevo()).isEqualTo(
                "{\"fin\": \"2026-11-26T22:00:00Z\", \"inicio\": \"2026-11-26T18:00:00Z\", \"motivo\": \"Curso\"}");
    }

    @Test
    void unRangoConTurnosActivosDevuelveTodos() {
        UUID tres = agenda.turno(ana, consulta, jueves(15), 30, "RESERVADO", false);
        UUID cuatro = agenda.turno(ana, consulta, jueves(16), 30, "CONFIRMADO", true);

        assertThat(propiedadesDelError(() -> crear.crear(jueves(15), jueves(19), null, odontologo),
                "BLOQUEO_CON_TURNOS"))
                .containsEntry("turnosEnConflicto", List.of(tres.toString(), cuatro.toString()));
        assertThat(acciones()).isEmpty();
    }

    @Test
    void unTurnoCanceladoNoImpideElBloqueoYLosBloqueosSePuedenSuperponer() {
        agenda.turno(ana, consulta, jueves(15), 30, "CANCELADO", false);
        crear.crear(jueves(14), jueves(20), null, odontologo);

        Bloqueo adentro = crear.crear(jueves(15), jueves(19), "Curso", odontologo);

        assertThat(bloqueos.buscar(consultorio, adentro.id())).isPresent();
    }

    @Test
    void soloElOdontologoBloqueaYConUnHorarioValido() {
        assertCodigo(() -> crear.crear(jueves(15), jueves(19), null,
                Autor.paciente(new PacienteId(ana), Optional.empty())), "ACCION_NO_PERMITIDA");
        assertCodigo(() -> crear.crear(jueves(15).plusSeconds(60), jueves(19), null, odontologo), "HORARIO_INVALIDO");
        assertCodigo(() -> crear.crear(jueves(19), jueves(15), null, odontologo), "HORARIO_INVALIDO");
    }

    @Test
    void eliminarUnBloqueoLoBorraYLoAudita() {
        Bloqueo bloqueo = crear.crear(jueves(15), jueves(19), "Curso", odontologo);

        eliminar.eliminar(bloqueo.id(), odontologo);

        assertThat(bloqueos.buscar(consultorio, bloqueo.id())).isEmpty();
        Evento evento = eventos().getLast();
        assertThat(evento.accion()).isEqualTo(EliminarBloqueo.ACCION);
        assertThat(evento.entidadId()).isEqualTo(bloqueo.id().valor());
        assertThat(evento.anterior()).isEqualTo(
                "{\"fin\": \"2026-11-26T22:00:00Z\", \"inicio\": \"2026-11-26T18:00:00Z\", \"motivo\": \"Curso\"}");
        assertThat(evento.nuevo()).isNull();
    }

    @Test
    void unBloqueoDeOtroConsultorioNoSeElimina() {
        AgendaDePrueba otra = AgendaDePrueba.nueva(jdbc);
        BloqueoId ajeno = new BloqueoId(otra.bloqueo(jueves(15), jueves(19), "Curso"));

        assertCodigo(() -> eliminar.eliminar(ajeno, odontologo), "RECURSO_NO_ENCONTRADO");
        assertThat(bloqueos.buscar(new ConsultorioId(otra.consultorio()), ajeno)).isPresent();
        assertThat(acciones()).isEmpty();
    }

    private static Instant jueves(int hora) {
        return JUEVES.plusSeconds(hora * 3600L);
    }
}
