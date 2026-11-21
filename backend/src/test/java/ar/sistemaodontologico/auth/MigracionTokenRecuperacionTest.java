package ar.sistemaodontologico.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.sistemaodontologico.compartido.IntegracionPostgresTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import pruebas.seguridad.CuentasDePrueba;
import pruebas.seguridad.CuentasDePrueba.Cuenta;

/** Migración de token_recuperacion (C-05, tarea 3.1; design D3). Datos ficticios (regla dura 17). */
class MigracionTokenRecuperacionTest extends IntegracionPostgresTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void guardaUnTokenDelUsuarioDeSuConsultorio() {
        Cuenta cuenta = CuentasDePrueba.crear(jdbc);

        UUID token = insertarToken(cuenta.consultorio(), cuenta.usuario(), hash());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM token_recuperacion WHERE id = ? "
                + "AND usado_at IS NULL AND revocado_at IS NULL", Integer.class, token)).isEqualTo(1);
    }

    @Test
    void elTokenNoPuedeApuntarAlUsuarioDeOtroConsultorio() {
        Cuenta una = CuentasDePrueba.crear(jdbc);
        Cuenta otra = CuentasDePrueba.crear(jdbc);

        assertThatThrownBy(() -> insertarToken(una.consultorio(), otra.usuario(), hash()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_token_recuperacion_usuario");
    }

    @Test
    void elHashDelTokenEsUnico() {
        Cuenta cuenta = CuentasDePrueba.crear(jdbc);
        String hash = hash();
        insertarToken(cuenta.consultorio(), cuenta.usuario(), hash);

        assertThatThrownBy(() -> insertarToken(cuenta.consultorio(), cuenta.usuario(), hash))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_token_recuperacion_hash");
    }

    @Test
    void elRolDeAplicacionNoBorraNiVaciaLosTokens() {
        Cuenta cuenta = CuentasDePrueba.crear(jdbc);
        UUID token = insertarToken(cuenta.consultorio(), cuenta.usuario(), hash());

        assertThatThrownBy(() -> jdbc.update("DELETE FROM token_recuperacion WHERE id = ?", token))
                .rootCause().hasMessageContaining("permission denied for table token_recuperacion");
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE token_recuperacion"))
                .rootCause().hasMessageContaining("permission denied for table token_recuperacion");
        assertThat(jdbc.update("UPDATE token_recuperacion SET usado_at = now() WHERE id = ?", token)).isEqualTo(1);
    }

    private UUID insertarToken(UUID consultorio, UUID usuario, String hash) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO token_recuperacion (id, consultorio_id, usuario_id, token_hash, expira_at)
                VALUES (?, ?, ?, ?, ?)""", id, consultorio, usuario, hash,
                Timestamp.from(Instant.parse("2026-11-20T13:30:00Z")));
        return id;
    }

    private static String hash() {
        return (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "").substring(0, 64);
    }
}
