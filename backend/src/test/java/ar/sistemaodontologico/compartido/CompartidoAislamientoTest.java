package ar.sistemaodontologico.compartido;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;
import ar.sistemaodontologico.compartido.application.TenantContext;
import pruebas.aislamiento.ControladorDeRecursosDePrueba;
import pruebas.aislamiento.FiltroDeTenantDePrueba;
import pruebas.aislamiento.RepositorioDeRecursosDePrueba;

/**
 * Aislamiento entre dos consultorios (C-02, tareas 4.2 a 4.4; spec aislamiento-tenant). Las tablas
 * {@code recurso_prueba} y {@code recurso_prueba_hijo} siguen db/migration/CONVENCIONES.md y se crean
 * solo para este test, con el rol propietario. Es el modelo del test de aislamiento de cada endpoint.
 */
@Import({ControladorDeRecursosDePrueba.class, CompartidoAislamientoTest.Configuracion.class})
class CompartidoAislamientoTest extends IntegracionPostgresTest {

    @TestConfiguration
    static class Configuracion {

        @Bean
        RepositorioDeRecursosDePrueba repositorioDeRecursosDePrueba(JdbcTemplate jdbc) {
            return new RepositorioDeRecursosDePrueba(jdbc);
        }

        @Bean
        FiltroDeTenantDePrueba filtroDeTenantDePrueba(TenantContext tenant) {
            return new FiltroDeTenantDePrueba(tenant);
        }
    }

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    private UUID consultorioA;
    private UUID consultorioB;

    private static boolean tablasCreadas;

    // En @BeforeEach y no en @BeforeAll: las tablas referencian consultorio, que crea Flyway al levantar el contexto.
    @BeforeEach
    void crearTablasDePrueba() throws SQLException {
        if (tablasCreadas) {
            return;
        }
        try (Connection owner = DriverManager.getConnection(POSTGRES.getJdbcUrl(), OWNER_USER, OWNER_PASSWORD);
                Statement sentencia = owner.createStatement()) {
            sentencia.execute("""
                    CREATE TABLE IF NOT EXISTS recurso_prueba (
                        id             UUID         NOT NULL,
                        consultorio_id UUID         NOT NULL,
                        nombre         VARCHAR(60)  NOT NULL,
                        CONSTRAINT pk_recurso_prueba PRIMARY KEY (id),
                        CONSTRAINT uk_recurso_prueba_consultorio_id UNIQUE (consultorio_id, id),
                        CONSTRAINT fk_recurso_prueba_consultorio FOREIGN KEY (consultorio_id) REFERENCES consultorio (id)
                    )""");
            sentencia.execute("""
                    CREATE TABLE IF NOT EXISTS recurso_prueba_hijo (
                        id             UUID NOT NULL,
                        consultorio_id UUID NOT NULL,
                        recurso_id     UUID NOT NULL,
                        CONSTRAINT pk_recurso_prueba_hijo PRIMARY KEY (id),
                        CONSTRAINT fk_recurso_prueba_hijo_consultorio FOREIGN KEY (consultorio_id) REFERENCES consultorio (id),
                        CONSTRAINT fk_recurso_prueba_hijo_recurso
                            FOREIGN KEY (consultorio_id, recurso_id) REFERENCES recurso_prueba (consultorio_id, id)
                    )""");
        }
        tablasCreadas = true;
    }

    @BeforeEach
    void crearDosConsultorios() {
        consultorioA = insertarConsultorio();
        consultorioB = insertarConsultorio();
    }

    // Requirement: Las relaciones entre datos de negocio no cruzan consultorios
    @Test
    void unHijoNoPuedeApuntarAUnRegistroDeOtroConsultorio() {
        UUID recursoDeA = insertarRecurso(consultorioA, "de A");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO recurso_prueba_hijo (id, consultorio_id, recurso_id) VALUES (?, ?, ?)",
                UUID.randomUUID(), consultorioB, recursoDeA))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_recurso_prueba_hijo_recurso");
    }

    @Test
    void unHijoPuedeApuntarAUnRegistroDelMismoConsultorio() {
        UUID recursoDeA = insertarRecurso(consultorioA, "de A");

        int filas = jdbc.update("INSERT INTO recurso_prueba_hijo (id, consultorio_id, recurso_id) VALUES (?, ?, ?)",
                UUID.randomUUID(), consultorioA, recursoDeA);

        assertThat(filas).isEqualTo(1);
    }

    // Requirement: Toda consulta de datos de negocio filtra por consultorio
    @Test
    void elListadoDeUnConsultorioNoVeLosRecursosDelOtro() throws Exception {
        UUID recursoDeA = insertarRecurso(consultorioA, "de A");
        insertarRecurso(consultorioB, "de B");

        ResponseEntity<String> respuesta = get("/api/v1/pruebas/recursos", consultorioA);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode lista = json.readTree(respuesta.getBody());
        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).path("id").asText()).isEqualTo(recursoDeA.toString());
    }

    // Requirement: Un recurso de otro consultorio responde como inexistente
    @Test
    void unRecursoAjenoRespondeNoEncontrado() throws Exception {
        UUID recursoDeB = insertarRecurso(consultorioB, "de B");

        ResponseEntity<String> respuesta = get("/api/v1/pruebas/recursos/" + recursoDeB, consultorioA);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(json.readTree(respuesta.getBody()).path("codigo").asText()).isEqualTo("RECURSO_NO_ENCONTRADO");
    }

    @Test
    void ajenoEInexistenteSonIndistinguibles() throws Exception {
        UUID recursoDeB = insertarRecurso(consultorioB, "de B");

        ResponseEntity<String> ajeno = get("/api/v1/pruebas/recursos/" + recursoDeB, consultorioA);
        ResponseEntity<String> inexistente = get("/api/v1/pruebas/recursos/" + UUID.randomUUID(), consultorioA);

        assertThat(ajeno.getStatusCode()).isEqualTo(inexistente.getStatusCode());
        assertThat(sinInstance(ajeno.getBody())).isEqualTo(sinInstance(inexistente.getBody()));
    }

    @Test
    void elDuenioSiVeSuRecurso() {
        UUID recursoDeB = insertarRecurso(consultorioB, "de B");

        assertThat(get("/api/v1/pruebas/recursos/" + recursoDeB, consultorioB).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // Requirement: El consultorio activo se resuelve solo desde el contexto (tarea 4.4)
    @Test
    void unConsultorioEnElBodyEsIgnorado() throws Exception {
        HttpHeaders cabeceras = cabeceras(consultorioA);
        cabeceras.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"consultorio_id\": \"" + consultorioB + "\", \"nombre\": \"intento\"}";

        ResponseEntity<String> respuesta = http.exchange("/api/v1/pruebas/recursos", HttpMethod.POST,
                new HttpEntity<>(body, cabeceras), String.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(json.readTree(respuesta.getBody()).path("consultorioId").asText()).isEqualTo(consultorioA.toString());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM recurso_prueba WHERE consultorio_id = ?", Integer.class,
                consultorioB)).isZero();
    }

    @Test
    void unConsultorioEnLaQueryEsIgnorado() throws Exception {
        insertarRecurso(consultorioB, "de B");

        ResponseEntity<String> respuesta = get("/api/v1/pruebas/recursos?consultorio_id=" + consultorioB, consultorioA);

        assertThat(json.readTree(respuesta.getBody())).isEmpty();
    }

    @Test
    void sinConsultorioActivoNoAccedeADatos() throws Exception {
        insertarRecurso(consultorioA, "de A");

        ResponseEntity<String> respuesta = http.getForEntity("/api/v1/pruebas/recursos", String.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(json.readTree(respuesta.getBody()).path("codigo").asText()).isEqualTo("ERROR_INTERNO");
        assertThat(respuesta.getBody()).doesNotContain("de A");
    }

    // Spec slug-consultorio — Scenario: Slug en uso (tarea 4.2)
    @Test
    void unSlugRepetidoRespondeSlugEnUso() throws Exception {
        String slug = "repetido-" + UUID.randomUUID().toString().substring(0, 8);
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> alta = new HttpEntity<>("{\"slug\": \"" + slug + "\"}", cabeceras);

        assertThat(http.postForEntity("/api/v1/pruebas/consultorios", alta, String.class).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        ResponseEntity<String> repetido = http.postForEntity("/api/v1/pruebas/consultorios", alta, String.class);

        assertThat(repetido.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json.readTree(repetido.getBody()).path("codigo").asText()).isEqualTo("SLUG_EN_USO");
    }

    private ResponseEntity<String> get(String ruta, UUID consultorioActivo) {
        return http.exchange(ruta, HttpMethod.GET, new HttpEntity<>(cabeceras(consultorioActivo)), String.class);
    }

    private static HttpHeaders cabeceras(UUID consultorioActivo) {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.set(FiltroDeTenantDePrueba.CABECERA, consultorioActivo.toString());
        return cabeceras;
    }

    private JsonNode sinInstance(String cuerpo) throws Exception {
        ObjectNode nodo = (ObjectNode) json.readTree(cuerpo);
        nodo.remove("instance");
        return nodo;
    }

    private UUID insertarConsultorio() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO consultorio (id, nombre, slug, plan) VALUES (?, 'Consultorio Demo', ?, 'PRUEBA')",
                id, "aislamiento-" + id.toString().substring(0, 8));
        return id;
    }

    private UUID insertarRecurso(UUID consultorio, String nombre) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO recurso_prueba (id, consultorio_id, nombre) VALUES (?, ?, ?)", id, consultorio, nombre);
        return id;
    }
}
