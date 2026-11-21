package ar.sistemaodontologico.consultorio;

import static org.assertj.core.api.Assertions.assertThat;

import ar.sistemaodontologico.compartido.IntegracionPostgresTest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Seed del perfil {@code local} (C-08, tareas 6.1 y 6.2; design D11). No se carga en los tests ni en el perfil por
 * defecto; el script se prueba aparte, con el rol propietario y en una transacción que se revierte.
 */
class SeedLocalTest extends IntegracionPostgresTest {

    private static final String SEED = "db/seed-local/R__demo.sql";
    /** Solo para desarrollo local; documentada en db/seed-local/README.md. */
    private static final String CONTRASENA_DE_DESARROLLO = "demo-local-2026";

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void elSeedNoSeCargaFueraDelPerfilLocal() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM consultorio WHERE slug = 'demo'", Integer.class)).isZero();
        // Los tests fijan las ubicaciones (IntegracionPostgresTest), así que se verifica la configuración de cada perfil.
        assertThat(ubicacionesDeFlyway("application.yml")).isEqualTo("classpath:db/migration");
        assertThat(ubicacionesDeFlyway("application-local.yml")).contains("classpath:db/seed-local");
    }

    private static String ubicacionesDeFlyway(String archivo) {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource(archivo));
        return yaml.getObject().getProperty("spring.flyway.locations");
    }

    @Test
    void elSeedCargaElConsultorioDemoYEsIdempotente() throws Exception {
        try (Connection owner = DriverManager.getConnection(POSTGRES.getJdbcUrl(), OWNER_USER, OWNER_PASSWORD)) {
            owner.setAutoCommit(false);
            try {
                ScriptUtils.executeSqlScript(owner, new ClassPathResource(SEED));
                ScriptUtils.executeSqlScript(owner, new ClassPathResource(SEED));

                assertThat(contar(owner, "SELECT count(*) FROM consultorio WHERE slug = 'demo'")).isEqualTo(1);
                assertThat(contar(owner, """
                        SELECT count(*) FROM tipo_prestacion p JOIN consultorio c ON c.id = p.consultorio_id
                        WHERE c.slug = 'demo' AND p.activa""")).isEqualTo(4);
                assertThat(contar(owner, """
                        SELECT count(*) FROM disponibilidad d JOIN consultorio c ON c.id = d.consultorio_id
                        WHERE c.slug = 'demo'""")).isEqualTo(10);
                assertThat(new BCryptPasswordEncoder().matches(CONTRASENA_DE_DESARROLLO,
                        texto(owner, "SELECT password_hash FROM usuario WHERE email = 'demo@consultorio.test'")))
                        .isTrue();
            } finally {
                owner.rollback();
            }
        }
    }

    private static int contar(Connection conexion, String sql) throws Exception {
        try (Statement sentencia = conexion.createStatement(); ResultSet fila = sentencia.executeQuery(sql)) {
            fila.next();
            return fila.getInt(1);
        }
    }

    private static String texto(Connection conexion, String sql) throws Exception {
        try (Statement sentencia = conexion.createStatement(); ResultSet fila = sentencia.executeQuery(sql)) {
            fila.next();
            return fila.getString(1);
        }
    }
}
