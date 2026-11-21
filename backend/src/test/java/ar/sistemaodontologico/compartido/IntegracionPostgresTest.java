package ar.sistemaodontologico.compartido;

import java.nio.file.Path;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * Base de los tests de integración (design D5): PostgreSQL 16 real en un contenedor
 * compartido por toda la suite, con los mismos roles que el entorno local.
 * Los changes siguientes extienden esta clase; nunca H2 ni repositorios mockeados.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class IntegracionPostgresTest {

    protected static final String OWNER_USER = "app_owner";
    protected static final String OWNER_PASSWORD = "owner-test";
    protected static final String APP_USER = "app_rw";
    protected static final String APP_PASSWORD = "app-test";
    protected static final String SECRETO_JWT_DE_PRUEBA =
            "c29sby1wYXJhLXRlc3RzLW5vLXVzYXItZW4tcHJvZHVjY2lvbi0wMTIzNDU2Nzg5YWI=";
    protected static final String ORIGEN_FRONTEND = "http://localhost:5173";

    private static final Path SCRIPT_ROLES = Path.of("..", "docker", "postgres", "init", "01-roles.sh");

    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("agenda")
            .withUsername("postgres")
            .withPassword("postgres-test")
            .withEnv("DB_OWNER_USER", OWNER_USER)
            .withEnv("DB_OWNER_PASSWORD", OWNER_PASSWORD)
            .withEnv("DB_APP_USER", APP_USER)
            .withEnv("DB_APP_PASSWORD", APP_PASSWORD)
            .withCopyFileToContainer(
                    MountableFile.forHostPath(SCRIPT_ROLES, 0755),
                    "/docker-entrypoint-initdb.d/01-roles.sh");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void propiedadesDeBaseDeDatos(DynamicPropertyRegistry registro) {
        registro.add("DB_URL", POSTGRES::getJdbcUrl);
        registro.add("DB_APP_USER", () -> APP_USER);
        registro.add("DB_APP_PASSWORD", () -> APP_PASSWORD);
        registro.add("DB_OWNER_USER", () -> OWNER_USER);
        registro.add("DB_OWNER_PASSWORD", () -> OWNER_PASSWORD);
        // Solo de test (C-04): 48 bytes fijos en base64; nunca se usa fuera de los tests.
        registro.add("JWT_SECRET", () -> SECRETO_JWT_DE_PRUEBA);
        registro.add("APP_FRONTEND_ORIGIN", () -> ORIGEN_FRONTEND);
    }
}
