package ar.sistemaodontologico.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.sistemaodontologico.compartido.IntegracionPostgresTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Migración de usuario y refresh_token (C-04, tarea 2.1; design D12). Datos ficticios (regla dura 17). */
class MigracionAuthTest extends IntegracionPostgresTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void altaDeUsuarioConValoresPorDefecto() {
        UUID consultorio = insertarConsultorio();
        UUID usuario = insertarUsuario(consultorio, "defaults-" + sufijo() + "@example.com");

        Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM usuario WHERE id = ?", usuario);

        assertThat(fila)
                .containsEntry("intentos_fallidos", 0)
                .containsEntry("credenciales_version", 1)
                .containsEntry("terminos_version", "2026-11-borrador");
        assertThat(fila.get("bloqueado_hasta")).isNull();
    }

    @Test
    void elEmailEsUnicoSinDistinguirMayusculas() {
        String email = "unico-" + sufijo() + "@example.com";
        insertarUsuario(insertarConsultorio(), email);

        assertThatThrownBy(() -> insertarUsuario(insertarConsultorio(), email.toUpperCase()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_usuario_email");
    }

    @Test
    void unConsultorioTieneUnSoloUsuario() {
        UUID consultorio = insertarConsultorio();
        insertarUsuario(consultorio, "uno-" + sufijo() + "@example.com");

        assertThatThrownBy(() -> insertarUsuario(consultorio, "otro-" + sufijo() + "@example.com"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_usuario_consultorio");
    }

    @Test
    void elRefreshNoPuedeApuntarAlUsuarioDeOtroConsultorio() {
        UUID consultorioA = insertarConsultorio();
        UUID usuarioDeA = insertarUsuario(consultorioA, "cruzado-" + sufijo() + "@example.com");
        UUID consultorioB = insertarConsultorio();

        assertThatThrownBy(() -> insertarRefresh(consultorioB, usuarioDeA))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_refresh_token_usuario");
    }

    @Test
    void elRefreshGuardaUnHashUnico() {
        UUID consultorio = insertarConsultorio();
        UUID usuario = insertarUsuario(consultorio, "hash-" + sufijo() + "@example.com");
        String hash = insertarRefresh(consultorio, usuario);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO refresh_token (id, consultorio_id, usuario_id, token_hash, expira_at)
                VALUES (?, ?, ?, ?, ?)""", UUID.randomUUID(), consultorio, usuario, hash, Timestamp.from(Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_refresh_token_hash");
    }

    @Test
    void elRolDeAplicacionNoBorraUsuariosNiRefresh() {
        UUID consultorio = insertarConsultorio();
        UUID usuario = insertarUsuario(consultorio, "permisos-" + sufijo() + "@example.com");
        insertarRefresh(consultorio, usuario);

        assertThatThrownBy(() -> jdbc.update("DELETE FROM refresh_token WHERE usuario_id = ?", usuario))
                .rootCause().hasMessageContaining("permission denied for table refresh_token");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM usuario WHERE id = ?", usuario))
                .rootCause().hasMessageContaining("permission denied for table usuario");
    }

    private UUID insertarConsultorio() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO consultorio (id, nombre, slug, plan) VALUES (?, 'Consultorio Demo', ?, 'PRUEBA')",
                id, "auth-" + id.toString().substring(0, 8));
        return id;
    }

    private UUID insertarUsuario(UUID consultorio, String email) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO usuario (id, consultorio_id, email, password_hash, nombre, apellido,
                                     password_cambiado_at, terminos_version, terminos_aceptados_at)
                VALUES (?, ?, ?, '$2a$12$hashficticiodeprueba', 'Ana', 'Prueba', now(), '2026-11-borrador', now())""",
                id, consultorio, email);
        return id;
    }

    private String insertarRefresh(UUID consultorio, UUID usuario) {
        String hash = (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "");
        jdbc.update("""
                INSERT INTO refresh_token (id, consultorio_id, usuario_id, token_hash, expira_at)
                VALUES (?, ?, ?, ?, ?)""", UUID.randomUUID(), consultorio, usuario, hash, Timestamp.from(Instant.now()));
        return hash;
    }

    private static String sufijo() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
