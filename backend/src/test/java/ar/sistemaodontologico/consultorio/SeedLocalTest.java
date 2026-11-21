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
                // C-10 (tarea 6.1, design D9): pacientes, turnos de la semana en curso y un bloqueo.
                assertThat(contar(owner, demo("paciente"))).isEqualTo(5);
                assertThat(contar(owner, demo("turno"))).isEqualTo(10);
                assertThat(contar(owner, demo("turno") + " AND es_sobreturno")).isEqualTo(1);
                assertThat(contar(owner, demo("bloqueo"))).isEqualTo(1);
                assertThat(contar(owner, """
                        SELECT count(*) FROM paciente p JOIN consultorio c ON c.id = p.consultorio_id
                        WHERE c.slug = 'demo' AND p.dni BETWEEN '90000001' AND '90000005'""")).isEqualTo(5);
                // El bloqueo no pisa turnos activos (RN-AG-05).
                assertThat(contar(owner, """
                        SELECT count(*) FROM turno t JOIN bloqueo b ON b.consultorio_id = t.consultorio_id
                        WHERE t.estado IN ('RESERVADO', 'CONFIRMADO')
                          AND tstzrange(t.inicio, t.fin, '[)') && tstzrange(b.inicio, b.fin, '[)')""")).isZero();
                assertThat(new BCryptPasswordEncoder().matches(CONTRASENA_DE_DESARROLLO,
                        texto(owner, "SELECT password_hash FROM usuario WHERE email = 'demo@consultorio.test'")))
                        .isTrue();
            } finally {
                owner.rollback();
            }
        }
    }

    private static String demo(String tabla) {
        return "SELECT count(*) FROM " + tabla + " x JOIN consultorio c ON c.id = x.consultorio_id WHERE c.slug = 'demo'";
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
