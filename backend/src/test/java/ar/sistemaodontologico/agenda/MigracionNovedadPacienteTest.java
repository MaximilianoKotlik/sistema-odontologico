package ar.sistemaodontologico.agenda;

import static org.assertj.core.api.Assertions.assertThat;

import ar.sistemaodontologico.compartido.IntegracionPostgresTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import pruebas.agenda.AgendaDePrueba;

/**
 * {@code turno.novedad_paciente_at} (C-12, tarea 1.2; C-15 D14, DD-30): la marca de un cambio del paciente que el
 * odontólogo no vio. Nula por defecto y con microsegundos, como la escribe C-15. Datos ficticios (regla dura 17).
 */
class MigracionNovedadPacienteTest extends IntegracionPostgresTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void laColumnaEsUnInstanteOpcional() {
        Map<String, Object> columna = jdbc.queryForMap("""
                SELECT data_type, is_nullable, column_default
                  FROM information_schema.columns
                 WHERE table_name = 'turno' AND column_name = 'novedad_paciente_at'""");

        assertThat(columna)
                .containsEntry("data_type", "timestamp with time zone")
                .containsEntry("is_nullable", "YES");
        assertThat(columna.get("column_default")).isNull();
    }

    @Test
    void unTurnoNuevoNaceSinNovedadYGuardaMicrosegundos() {
        AgendaDePrueba agenda = AgendaDePrueba.nueva(jdbc);
        UUID turno = agenda.turno(agenda.paciente("Ana", "Prueba"), agenda.prestacion("Consulta", 30, true),
                AgendaDePrueba.lunesA("10:00"), 30, "RESERVADO", false);
        assertThat(jdbc.queryForObject("SELECT novedad_paciente_at FROM turno WHERE id = ?", Timestamp.class, turno))
                .isNull();

        Instant conMicros = Instant.parse("2026-11-23T13:00:00.123456Z");
        jdbc.update("UPDATE turno SET novedad_paciente_at = ? WHERE id = ?", Timestamp.from(conMicros), turno);

        assertThat(jdbc.queryForObject("SELECT novedad_paciente_at FROM turno WHERE id = ?", Timestamp.class, turno)
                .toInstant()).isEqualTo(conMicros);
    }
}
