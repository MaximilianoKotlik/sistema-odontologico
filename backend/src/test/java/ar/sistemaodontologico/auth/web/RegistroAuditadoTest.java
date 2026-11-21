package ar.sistemaodontologico.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static pruebas.seguridad.ClienteDeAuth.CONTRASENA;

import ar.sistemaodontologico.compartido.IntegracionPostgresTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import pruebas.seguridad.ClienteDeAuth;

/** C-05, spec registro-odontologo: Requirement de auditoría del registro (tarea 2.1, design D9). */
class RegistroAuditadoTest extends IntegracionPostgresTest {

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    private ClienteDeAuth auth;

    @BeforeEach
    void cliente() {
        auth = new ClienteDeAuth(http, ORIGEN_FRONTEND);
    }

    // Scenario: Registro auditado
    @Test
    void elRegistroDejaUnEventoCuentaCreadaSinSecretos() throws Exception {
        String email = auth.registrar("registro-auditado");

        List<Map<String, Object>> eventos = jdbc.queryForList("""
                SELECT e.consultorio_id, e.actor_tipo, e.actor_id, e.entidad, e.entidad_id, host(e.ip) AS ip,
                       e.valor_anterior::text AS valor_anterior, e.valor_nuevo::text AS valor_nuevo,
                       u.id AS usuario, u.consultorio_id AS consultorio, u.password_hash, c.slug, c.prueba_hasta
                FROM evento_auditoria e
                JOIN usuario u ON u.consultorio_id = e.consultorio_id
                JOIN consultorio c ON c.id = u.consultorio_id
                WHERE u.email = ? AND e.accion = 'CUENTA_CREADA'""", email);

        assertThat(eventos).hasSize(1);
        Map<String, Object> evento = eventos.get(0);
        assertThat(evento.get("consultorio_id")).isEqualTo(evento.get("consultorio"));
        assertThat(evento.get("actor_tipo")).isEqualTo("ODONTOLOGO");
        assertThat(evento.get("actor_id")).isEqualTo(evento.get("usuario"));
        assertThat(evento.get("entidad")).isEqualTo("usuario");
        assertThat(evento.get("entidad_id")).isEqualTo(evento.get("usuario"));
        assertThat(evento.get("ip")).isNotNull();
        assertThat(evento.get("valor_anterior")).isNull();
        JsonNode valor = json.readTree((String) evento.get("valor_nuevo"));
        assertThat(valor.path("slug").asText()).isEqualTo(evento.get("slug"));
        assertThat(valor.path("plan").asText()).isEqualTo("PRUEBA");
        assertThat(valor.path("prueba_hasta").asText()).isEqualTo(String.valueOf(evento.get("prueba_hasta")));
        assertThat((String) evento.get("valor_nuevo"))
                .doesNotContain(CONTRASENA)
                .doesNotContain((String) evento.get("password_hash"))
                .doesNotContain(email);
    }

    // Scenario: Registro rechazado no deja evento
    @Test
    void unRegistroConEmailEnUsoNoDejaEvento() {
        String email = auth.registrar("registro-repetido");
        long antes = eventosCuentaCreada();

        assertThat(auth.post("/api/v1/auth/registro", Map.of(
                "nombre", "Ana", "apellido", "Prueba", "email", email, "password", CONTRASENA,
                "nombreConsultorio", "Consultorio Repetido", "aceptaTerminos", true)).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        assertThat(eventosCuentaCreada()).isEqualTo(antes);
    }

    private long eventosCuentaCreada() {
        return jdbc.queryForObject("SELECT count(*) FROM evento_auditoria WHERE accion = 'CUENTA_CREADA'", Long.class);
    }
}
