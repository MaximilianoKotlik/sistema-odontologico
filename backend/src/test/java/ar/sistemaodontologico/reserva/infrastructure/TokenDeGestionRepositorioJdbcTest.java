package ar.sistemaodontologico.reserva.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.sistemaodontologico.compartido.IntegracionPostgresTest;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import ar.sistemaodontologico.reserva.application.TokenDeGestionRepositorio;
import ar.sistemaodontologico.reserva.application.TurnoPorToken;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import pruebas.agenda.AgendaDePrueba;

/** Tabla {@code token_gestion_turno} y sus dos niveles de vigencia (C-15, tareas 4.1 a 4.3, design D3 y D14). */
class TokenDeGestionRepositorioJdbcTest extends IntegracionPostgresTest {

    private static final Instant AHORA = Instant.parse("2030-01-07T12:00:00Z");

    @Autowired
    private TokenDeGestionRepositorio tokens;

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

    @Test
    void elHashEsUnico() {
        String hash = hash();
        tokens.insertar(consultorio, UUID.randomUUID(), turno("RESERVADO", 1), hash);

        assertThatThrownBy(() -> tokens.insertar(consultorio, UUID.randomUUID(), turno("RESERVADO", 2), hash))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ux_token_hash");
    }

    @Test
    void unTokenPorTurno() {
        UUID turno = turno("RESERVADO", 1);
        tokens.insertar(consultorio, UUID.randomUUID(), turno, hash());

        assertThatThrownBy(() -> tokens.insertar(consultorio, UUID.randomUUID(), turno, hash()))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ux_token_turno");
    }

    @Test
    void noApuntaAUnTurnoDeOtroConsultorio() {
        AgendaDePrueba otra = AgendaDePrueba.nueva(jdbc);
        UUID turnoAjeno = otra.turno(otra.paciente("Otra", "Persona"), otra.prestacion("Consulta", 30, true),
                AHORA.plus(Duration.ofDays(1)), 30, "RESERVADO", false);

        assertThatThrownBy(() -> tokens.insertar(consultorio, UUID.randomUUID(), turnoAjeno, hash()))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("fk_token_turno");
    }

    @Test
    void laAplicacionNoPuedeBorrarTokens() {
        tokens.insertar(consultorio, UUID.randomUUID(), turno("RESERVADO", 1), hash());

        assertThatThrownBy(() -> jdbc.update("DELETE FROM token_gestion_turno WHERE consultorio_id = ?",
                consultorio.valor())).isInstanceOf(DataAccessException.class).rootCause()
                .hasMessageContaining("permission denied for table token_gestion_turno");
    }

    @Test
    void vigenteSeConsultaYSeModifica() {
        UUID turno = turno("CONFIRMADO", 1);
        String hash = hash();
        tokens.insertar(consultorio, UUID.randomUUID(), turno, hash);

        TurnoPorToken encontrado = tokens.buscarModificable(hash, AHORA).orElseThrow();

        assertThat(encontrado.turno()).isEqualTo(turno);
        assertThat(encontrado.consultorio()).isEqualTo(consultorio);
        assertThat(encontrado.prestacion()).isEqualTo("Consulta");
        assertThat(encontrado.nombre()).isEqualTo("Ana");
        assertThat(encontrado.duracionMin()).isEqualTo(30);
        assertThat(tokens.buscarConsultable(hash, AHORA)).isPresent();
    }

    @Test
    void revocadoSeConsultaPeroNoSeModifica() {
        UUID turno = turno("CANCELADO", 1);
        String hash = hash();
        tokens.insertar(consultorio, UUID.randomUUID(), turno, hash);
        tokens.revocar(consultorio, turno, AHORA);

        assertThat(tokens.buscarConsultable(hash, AHORA)).isPresent();
        assertThat(tokens.buscarModificable(hash, AHORA)).isEmpty();
    }

    @Test
    void unTurnoActivoConTokenRevocadoNoSeModifica() {
        UUID turno = turno("RESERVADO", 1);
        String hash = hash();
        tokens.insertar(consultorio, UUID.randomUUID(), turno, hash);
        tokens.revocar(consultorio, turno, AHORA);

        assertThat(tokens.buscarModificable(hash, AHORA)).isEmpty();
    }

    @Test
    void canceladoPorElOdontologoSeConsultaPeroNoSeModificaAunSinRevocar() {
        String hash = hash();
        tokens.insertar(consultorio, UUID.randomUUID(), turno("CANCELADO", 1), hash);

        TurnoPorToken visto = tokens.buscarConsultable(hash, AHORA).orElseThrow();

        assertThat(visto.canceladoPor()).isEqualTo("ODONTOLOGO");
        assertThat(tokens.buscarModificable(hash, AHORA)).isEmpty();
    }

    @Test
    void ausenteNoSeConsultaNiSeModifica() {
        String hash = hash();
        tokens.insertar(consultorio, UUID.randomUUID(), turno("AUSENTE", 1), hash);

        assertThat(tokens.buscarConsultable(hash, AHORA)).isEmpty();
        assertThat(tokens.buscarModificable(hash, AHORA)).isEmpty();
    }

    @Test
    void terminadoNoSeConsultaNiSeModifica() {
        String hash = hash();
        tokens.insertar(consultorio, UUID.randomUUID(), turno("RESERVADO", 1), hash);
        Instant finDelTurno = AHORA.plus(Duration.ofDays(1)).plus(Duration.ofMinutes(30));

        assertThat(tokens.buscarConsultable(hash, finDelTurno)).isEmpty();
        assertThat(tokens.buscarModificable(hash, finDelTurno)).isEmpty();
    }

    @Test
    void hashInexistenteNoSeEncuentra() {
        assertThat(tokens.buscarConsultable(hash(), AHORA)).isEmpty();
    }

    @Test
    void revocarNoTocaElTokenDeOtroConsultorio() {
        UUID turno = turno("RESERVADO", 1);
        String hash = hash();
        tokens.insertar(consultorio, UUID.randomUUID(), turno, hash);

        tokens.revocar(new ConsultorioId(AgendaDePrueba.nueva(jdbc).consultorio()), turno, AHORA);

        assertThat(tokens.buscarModificable(hash, AHORA)).isPresent();
    }

    @Test
    void elTurnoTieneLaColumnaDeNovedadVacia() {
        UUID turno = turno("RESERVADO", 1);

        Timestamp novedad = jdbc.queryForObject("SELECT novedad_paciente_at FROM turno WHERE id = ?", Timestamp.class,
                turno);

        assertThat(novedad).isNull();
    }

    private UUID turno(String estado, int dias) {
        return agenda.turno(paciente, prestacion, AHORA.plus(Duration.ofDays(dias)), 30, estado, false);
    }

    private static String hash() {
        return (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "");
    }
}
