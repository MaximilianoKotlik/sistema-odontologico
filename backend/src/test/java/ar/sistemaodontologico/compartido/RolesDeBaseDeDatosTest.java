package ar.sistemaodontologico.compartido;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Spec plataforma/entorno-local — Requirement: Roles de base de datos separados. */
class RolesDeBaseDeDatosTest extends IntegracionPostgresTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void laAplicacionAtiendeConElRolDeAplicacion() {
        assertThat(jdbc.queryForObject("SELECT current_user", String.class)).isEqualTo(APP_USER);
    }

    @Test
    void elRolDeAplicacionNoPuedeCrearTablas() {
        assertThatThrownBy(() -> jdbc.execute("CREATE TABLE intrusa (id int)"))
                .rootCause()
                .hasMessageContaining("permission denied for schema public");
    }

    @Test
    void elRolDeAplicacionAccedeATablasNuevasDelPropietario() throws SQLException {
        try (Connection owner = DriverManager.getConnection(POSTGRES.getJdbcUrl(), OWNER_USER, OWNER_PASSWORD);
                Statement sentencia = owner.createStatement()) {
            sentencia.execute("CREATE TABLE IF NOT EXISTS prueba_permisos (id int PRIMARY KEY)");
        }

        jdbc.update("INSERT INTO prueba_permisos (id) VALUES (1) ON CONFLICT DO NOTHING");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM prueba_permisos", Integer.class)).isEqualTo(1);
    }

    @Test
    void lasMigracionesCorrenConElRolPropietario() {
        // Sin migraciones todavía, Flyway igual crea su tabla de historial: su dueño prueba con qué rol corrió.
        String duenioDelHistorial = jdbc.queryForObject(
                "SELECT tableowner FROM pg_tables WHERE tablename = 'flyway_schema_history'", String.class);

        assertThat(duenioDelHistorial).isEqualTo(OWNER_USER);
    }
}
