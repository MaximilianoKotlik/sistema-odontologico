package ar.sistemaodontologico.auditoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.sistemaodontologico.compartido.IntegracionPostgresTest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Migración de {@code evento_auditoria} (C-03, tareas 1.1 a 1.4). Spec auditoria — Requirements:
 * Persistencia del evento publicado, Inmutabilidad del registro y Pertenencia a un consultorio.
 */
class MigracionEventoAuditoriaTest extends IntegracionPostgresTest {

    private static final Instant OCURRIDO = Instant.parse("2026-12-01T13:00:00Z");

    @Autowired
    private JdbcTemplate jdbc;

    private UUID consultorio;

    @BeforeEach
    void crearConsultorio() {
        consultorio = insertarConsultorio();
    }

    @Test
    void guardaYReleeUnEventoCompleto() {
        UUID evento = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        UUID turno = UUID.randomUUID();

        jdbc.update("""
                INSERT INTO evento_auditoria (id, consultorio_id, ocurrido_at, actor_tipo, actor_id, entidad,
                    entidad_id, accion, valor_anterior, valor_nuevo, ip)
                VALUES (?, ?, ?, 'ODONTOLOGO', ?, 'turno', ?, 'TURNO_CREADO', NULL,
                    CAST(? AS jsonb), CAST(? AS inet))
                """,
                evento, consultorio, Timestamp.from(OCURRIDO), actor, turno,
                "{\"estado\": \"RESERVADO\", \"duracion_min\": 30}", "203.0.113.7");

        Map<String, Object> fila = jdbc.queryForMap("""
                SELECT consultorio_id, ocurrido_at, actor_tipo, actor_id, entidad, entidad_id, accion,
                    valor_anterior, valor_nuevo::text AS valor_nuevo, host(ip) AS ip
                FROM evento_auditoria WHERE id = ?
                """, evento);

        assertThat(fila)
                .containsEntry("consultorio_id", consultorio)
                .containsEntry("actor_tipo", "ODONTOLOGO")
                .containsEntry("actor_id", actor)
                .containsEntry("entidad", "turno")
                .containsEntry("entidad_id", turno)
                .containsEntry("accion", "TURNO_CREADO")
                .containsEntry("valor_anterior", null)
                .containsEntry("valor_nuevo", "{\"estado\": \"RESERVADO\", \"duracion_min\": 30}")
                .containsEntry("ip", "203.0.113.7");
        assertThat(((Timestamp) fila.get("ocurrido_at")).toInstant()).isEqualTo(OCURRIDO);
    }

    @Test
    void laAplicacionNoPuedeModificarBorrarNiVaciarElRegistro() {
        UUID evento = insertarEvento(consultorio);

        assertThatThrownBy(() -> jdbc.update("UPDATE evento_auditoria SET accion = 'OTRA' WHERE id = ?", evento))
                .rootCause().hasMessageContaining("permission denied for table evento_auditoria");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM evento_auditoria WHERE id = ?", evento))
                .rootCause().hasMessageContaining("permission denied for table evento_auditoria");
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE evento_auditoria"))
                .rootCause().hasMessageContaining("permission denied for table evento_auditoria");

        assertThat(jdbc.queryForObject("SELECT accion FROM evento_auditoria WHERE id = ?", String.class, evento))
                .isEqualTo("TURNO_CREADO");
    }

    @Test
    void elTriggerFrenaAunAUnRolConTodosLosPrivilegios() throws SQLException {
        // app_owner es dueño de la tabla: tiene todos los privilegios, así que solo lo puede frenar el trigger.
        UUID evento = insertarEvento(consultorio);

        try (Connection owner = DriverManager.getConnection(POSTGRES.getJdbcUrl(), OWNER_USER, OWNER_PASSWORD);
                Statement sentencia = owner.createStatement()) {
            assertThatThrownBy(() -> sentencia.execute(
                    "UPDATE evento_auditoria SET accion = 'OTRA' WHERE id = '" + evento + "'"))
                    .hasMessageContaining("append-only");
            assertThatThrownBy(() -> sentencia.execute("DELETE FROM evento_auditoria WHERE id = '" + evento + "'"))
                    .hasMessageContaining("append-only");
            assertThatThrownBy(() -> sentencia.execute("TRUNCATE evento_auditoria"))
                    .hasMessageContaining("append-only");
        }

        assertThat(jdbc.queryForObject("SELECT count(*) FROM evento_auditoria WHERE id = ?", Integer.class, evento))
                .isEqualTo(1);
    }

    @Test
    void rechazaUnEventoDeUnConsultorioInexistente() {
        assertThatThrownBy(() -> insertarEvento(UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_evento_auditoria_consultorio");
    }

    @Test
    void rechazaUnTipoDeActorDesconocido() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO evento_auditoria (id, consultorio_id, actor_tipo, entidad, entidad_id, accion)
                VALUES (?, ?, 'SECRETARIA', 'turno', ?, 'TURNO_CREADO')
                """, UUID.randomUUID(), consultorio, UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_evento_auditoria_actor_tipo");
    }

    @Test
    void aceptaUnOdontologoSinIdParaIntentosNoAutenticados() {
        // C-05 D9: LOGIN_FALLIDO tiene actor ODONTOLOGO sin id, porque todavía no hay sesión. Por eso la base no
        // exige actor_id para los tipos distintos de SISTEMA.
        jdbc.update("""
                INSERT INTO evento_auditoria (id, consultorio_id, actor_tipo, entidad, entidad_id, accion)
                VALUES (?, ?, 'ODONTOLOGO', 'usuario', ?, 'LOGIN_FALLIDO')
                """, UUID.randomUUID(), consultorio, UUID.randomUUID());

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM evento_auditoria WHERE consultorio_id = ? AND actor_id IS NULL",
                Integer.class, consultorio)).isEqualTo(1);
    }

    private UUID insertarEvento(UUID deConsultorio) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO evento_auditoria (id, consultorio_id, ocurrido_at, actor_tipo, entidad, entidad_id, accion)
                VALUES (?, ?, ?, 'SISTEMA', 'turno', ?, 'TURNO_CREADO')
                """, id, deConsultorio, Timestamp.from(OCURRIDO), UUID.randomUUID());
        return id;
    }

    private UUID insertarConsultorio() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO consultorio (id, nombre, slug, plan) VALUES (?, 'Consultorio Demo', ?, 'PRUEBA')",
                id, "auditoria-" + id.toString().substring(0, 8));
        return id;
    }
}
