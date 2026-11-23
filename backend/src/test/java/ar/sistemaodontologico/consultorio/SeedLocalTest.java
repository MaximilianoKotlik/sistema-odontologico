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
    private static final String SEED_VENCIDO = "db/seed-local/R__demo_vencido.sql";
    /** Token de gestión de demo en claro, documentado en db/seed-local/README.md (C-23, design D8). Solo local. */
    private static final String TOKEN_DE_GESTION_DE_DEMO = "demo-gestion-de-turno-solo-local-0000000001";
    /** Solo para desarrollo local; documentada en db/seed-local/README.md. */
    private static final String CONTRASENA_DE_DESARROLLO = "demo-local-2026";

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void elSeedNoSeCargaFueraDelPerfilLocal() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM consultorio WHERE slug IN ('demo', 'demo-vencido')",
                Integer.class)).isZero();
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
                // La demo es un consultorio pago: sin aviso de prueba ni bloqueo de solo lectura.
                assertThat(texto(owner, "SELECT plan FROM consultorio WHERE slug = 'demo'")).isEqualTo("ACTIVO");
                assertThat(texto(owner, "SELECT prueba_hasta::text FROM consultorio WHERE slug = 'demo'")).isNull();
                assertThat(contar(owner, """
                        SELECT count(*) FROM tipo_prestacion p JOIN consultorio c ON c.id = p.consultorio_id
                        WHERE c.slug = 'demo' AND p.activa""")).isEqualTo(4);
                assertThat(contar(owner, """
                        SELECT count(*) FROM disponibilidad d JOIN consultorio c ON c.id = d.consultorio_id
                        WHERE c.slug = 'demo'""")).isEqualTo(10);
                // C-10 (tarea 6.1, design D9): pacientes, turnos de la semana en curso y un bloqueo.
                assertThat(contar(owner, demo("paciente"))).isEqualTo(6);
                assertThat(contar(owner, demo("turno"))).isEqualTo(14);
                assertThat(contar(owner, demo("turno") + " AND es_sobreturno")).isEqualTo(1);
                assertThat(contar(owner, demo("bloqueo"))).isEqualTo(1);
                assertThat(contar(owner, """
                        SELECT count(*) FROM paciente p JOIN consultorio c ON c.id = p.consultorio_id
                        WHERE c.slug = 'demo' AND p.dni BETWEEN '90000001' AND '90000006'""")).isEqualTo(6);
                // El bloqueo no pisa turnos activos (RN-AG-05).
                assertThat(contar(owner, """
                        SELECT count(*) FROM turno t JOIN bloqueo b ON b.consultorio_id = t.consultorio_id
                        WHERE t.estado IN ('RESERVADO', 'CONFIRMADO')
                          AND tstzrange(t.inicio, t.fin, '[)') && tstzrange(b.inicio, b.fin, '[)')""")).isZero();
                assertThat(new BCryptPasswordEncoder().matches(CONTRASENA_DE_DESARROLLO,
                        texto(owner, "SELECT password_hash FROM usuario WHERE email = 'demo@consultorio.test'")))
                        .isTrue();
                agendaDeDemoCompleta(owner);
            } finally {
                owner.rollback();
            }
        }
    }

    @Test
    void elSeedPasaAActivoUnaDemoQueYaEstabaCargadaEnPrueba() throws Exception {
        try (Connection owner = DriverManager.getConnection(POSTGRES.getJdbcUrl(), OWNER_USER, OWNER_PASSWORD)) {
            owner.setAutoCommit(false);
            try {
                ScriptUtils.executeSqlScript(owner, new ClassPathResource(SEED));
                try (Statement sentencia = owner.createStatement()) {
                    sentencia.executeUpdate("""
                            UPDATE consultorio SET plan = 'PRUEBA', prueba_hasta = DATE '2099-12-31'
                             WHERE slug = 'demo'""");
                }

                ScriptUtils.executeSqlScript(owner, new ClassPathResource(SEED));

                assertThat(texto(owner, "SELECT plan FROM consultorio WHERE slug = 'demo'")).isEqualTo("ACTIVO");
                assertThat(texto(owner, "SELECT prueba_hasta::text FROM consultorio WHERE slug = 'demo'")).isNull();
            } finally {
                owner.rollback();
            }
        }
    }

    /** C-23 (tarea 4.1, design D8; spec datos-de-demo §Agenda de demo completa y §Casos especiales). */
    private static void agendaDeDemoCompleta(Connection owner) throws Exception {
        for (String estado : new String[] {"RESERVADO", "CONFIRMADO", "CANCELADO", "AUSENTE"}) {
            assertThat(contar(owner, demo("turno") + " AND estado = '" + estado + "'")).as(estado).isPositive();
        }
        assertThat(contar(owner, demo("turno") + " AND origen = 'LINK'")).isGreaterThanOrEqualTo(2);
        // La novedad sin ver es de un turno que el paciente cambió por el link.
        assertThat(contar(owner, demo("turno") + " AND origen = 'LINK' AND novedad_paciente_at IS NOT NULL"))
                .isEqualTo(1);
        // Un paciente para revisar con lo que informó al reservar (DD-38) y el turno de esa reserva.
        assertThat(contar(owner, demo("paciente") + """
                 AND requiere_revision AND nombre_informado IS NOT NULL AND informado_at IS NOT NULL
                 AND informado_turno_id IS NOT NULL""")).isEqualTo(1);
        // Eventos de auditoría de esos cambios, con un turno reprogramado por el paciente.
        assertThat(contar(owner, demo("evento_auditoria") + " AND accion = 'TURNO_CREADO'")).isPositive();
        assertThat(contar(owner, demo("evento_auditoria") + " AND accion = 'TURNO_ESTADO_CAMBIADO'")).isPositive();
        assertThat(contar(owner, demo("evento_auditoria")
                + " AND accion = 'TURNO_REPROGRAMADO' AND actor_tipo = 'PACIENTE'")).isEqualTo(1);
        // El token de gestión documentado abre un turno reservado por link (se guarda solo su hash).
        assertThat(contar(owner, """
                SELECT count(*) FROM token_gestion_turno g
                  JOIN turno t ON t.consultorio_id = g.consultorio_id AND t.id = g.turno_id
                  JOIN consultorio c ON c.id = g.consultorio_id
                 WHERE c.slug = 'demo' AND t.origen = 'LINK' AND t.estado = 'RESERVADO' AND g.revocado_at IS NULL
                   AND g.token_hash = '%s'""".formatted(sha256(TOKEN_DE_GESTION_DE_DEMO)))).isEqualTo(1);
    }

    // C-23 (tarea 4.3): segundo consultorio, con el plan vencido, para ver la solo lectura y la exportación.
    @Test
    void elSeedCargaElConsultorioVencidoYEsIdempotente() throws Exception {
        try (Connection owner = DriverManager.getConnection(POSTGRES.getJdbcUrl(), OWNER_USER, OWNER_PASSWORD)) {
            owner.setAutoCommit(false);
            try {
                ScriptUtils.executeSqlScript(owner, new ClassPathResource(SEED_VENCIDO));
                ScriptUtils.executeSqlScript(owner, new ClassPathResource(SEED_VENCIDO));

                assertThat(contar(owner, "SELECT count(*) FROM consultorio WHERE slug = 'demo-vencido'")).isEqualTo(1);
                assertThat(texto(owner, "SELECT plan FROM consultorio WHERE slug = 'demo-vencido'"))
                        .isEqualTo("VENCIDO");
                // La página pública muestra el teléfono para pedir turno (no toma turnos online con el plan vencido).
                assertThat(texto(owner, "SELECT telefono_contacto FROM consultorio WHERE slug = 'demo-vencido'"))
                        .startsWith("+54911000000");
                assertThat(new BCryptPasswordEncoder().matches(CONTRASENA_DE_DESARROLLO,
                        texto(owner, "SELECT password_hash FROM usuario WHERE email = 'vencido@consultorio.test'")))
                        .isTrue();
                // Hay algo que ver y exportar en solo lectura.
                assertThat(contar(owner, vencido("tipo_prestacion"))).isPositive();
                assertThat(contar(owner, vencido("paciente"))).isPositive();
                assertThat(contar(owner, vencido("turno"))).isPositive();
            } finally {
                owner.rollback();
            }
        }
    }

    private static String vencido(String tabla) {
        return "SELECT count(*) FROM " + tabla
                + " x JOIN consultorio c ON c.id = x.consultorio_id WHERE c.slug = 'demo-vencido'";
    }

    private static String sha256(String valor) throws Exception {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(valor.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
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
