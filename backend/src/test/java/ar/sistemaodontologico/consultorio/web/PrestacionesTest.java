package ar.sistemaodontologico.consultorio.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.sistemaodontologico.compartido.IntegracionPostgresTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pruebas.seguridad.CuentasDePrueba;
import pruebas.seguridad.CuentasDePrueba.Cuenta;
import pruebas.seguridad.TokensDePrueba;

/** {@code /api/v1/prestaciones} (C-08, tareas 4.2 a 4.4; spec prestaciones). Datos ficticios (regla dura 17). */
@AutoConfigureMockMvc
class PrestacionesTest extends IntegracionPostgresTest {

    private static final String RUTA = "/api/v1/prestaciones";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    private Cuenta cuenta;
    private String token;

    @BeforeEach
    void crearCuenta() {
        cuenta = CuentasDePrueba.crear(jdbc);
        token = tokenDe(cuenta);
    }

    // --- Alta ---

    @Test
    void altaDeLimpiezaDe45Minutos() throws Exception {
        crear(token, Map.of("nombre", "Limpieza", "duracionMin", 45))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.nombre").value("Limpieza"))
                .andExpect(jsonPath("$.duracionMin").value(45))
                .andExpect(jsonPath("$.activa").value(true))
                .andExpect(jsonPath("$.visiblePublica").value(true));
    }

    @Test
    void duracionInvalida() throws Exception {
        for (int duracion : new int[] {47, 0, 485}) {
            crear(token, Map.of("nombre", "Prestación " + duracion, "duracionMin", duracion))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("DURACION_INVALIDA"));
        }
    }

    @Test
    void nombreObligatorio() throws Exception {
        crear(token, Map.of("nombre", " ", "duracionMin", 30))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value("nombre"));
    }

    @Test
    void nombreRepetidoConOtrasMayusculas() throws Exception {
        crear(token, Map.of("nombre", "Limpieza", "duracionMin", 45)).andExpect(status().isCreated());

        crear(token, Map.of("nombre", "LIMPIEZA", "duracionMin", 30))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PRESTACION_DUPLICADA"));
    }

    @Test
    void mismoNombreEnOtroConsultorio() throws Exception {
        crear(token, Map.of("nombre", "Limpieza", "duracionMin", 45)).andExpect(status().isCreated());

        crear(tokenDe(CuentasDePrueba.crear(jdbc)), Map.of("nombre", "Limpieza", "duracionMin", 45))
                .andExpect(status().isCreated());
    }

    @Test
    void sinOrdenVaAlFinal() throws Exception {
        crear(token, Map.of("nombre", "Consulta", "duracionMin", 30, "orden", 5)).andExpect(status().isCreated());

        crear(token, Map.of("nombre", "Control", "duracionMin", 20))
                .andExpect(jsonPath("$.orden").value(6));
    }

    // --- Edición ---

    @Test
    void cambioDeDuracion() throws Exception {
        String id = idDe(crear(token, Map.of("nombre", "Limpieza", "duracionMin", 45)));

        editar(token, id, Map.of("nombre", "Limpieza", "duracionMin", 60, "visiblePublica", true, "orden", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duracionMin").value(60));
        consultar(token, id).andExpect(jsonPath("$.duracionMin").value(60));
    }

    @Test
    void editarAlNombreDeOtraPrestacion() throws Exception {
        crear(token, Map.of("nombre", "Limpieza", "duracionMin", 45));
        String control = idDe(crear(token, Map.of("nombre", "Control", "duracionMin", 20)));

        editar(token, control, Map.of("nombre", "limpieza", "duracionMin", 20, "visiblePublica", true, "orden", 2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PRESTACION_DUPLICADA"));
    }

    @Test
    void editarCambiandoSoloLasMayusculasDeSuNombre() throws Exception {
        String id = idDe(crear(token, Map.of("nombre", "limpieza", "duracionMin", 45)));

        editar(token, id, Map.of("nombre", "Limpieza", "duracionMin", 45, "visiblePublica", true, "orden", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Limpieza"));
    }

    @Test
    void editarConDuracionInvalida() throws Exception {
        String id = idDe(crear(token, Map.of("nombre", "Limpieza", "duracionMin", 45)));

        editar(token, id, Map.of("nombre", "Limpieza", "duracionMin", 47, "visiblePublica", true, "orden", 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DURACION_INVALIDA"));
    }

    // --- Desactivar, activar y sin borrado ---

    @Test
    void desactivarYReactivar() throws Exception {
        String id = idDe(crear(token, Map.of("nombre", "Limpieza", "duracionMin", 45)));

        accion(token, id, "desactivar").andExpect(status().isOk()).andExpect(jsonPath("$.activa").value(false));
        listar(token, "").andExpect(jsonPath("$[*].nombre", not(hasItem("Limpieza"))));

        accion(token, id, "activar").andExpect(status().isOk()).andExpect(jsonPath("$.activa").value(true));
        listar(token, "").andExpect(jsonPath("$[*].nombre", hasItem("Limpieza")));
    }

    @Test
    void sinBorrado() throws Exception {
        String id = idDe(crear(token, Map.of("nombre", "Limpieza", "duracionMin", 45)));

        mvc.perform(conToken(delete(RUTA + "/" + id), token))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.codigo").value("METODO_NO_PERMITIDO"));
        consultar(token, id).andExpect(status().isOk());
    }

    // --- Listado ---

    @Test
    void listadoPorDefectoOrdenadoYSinInactivas() throws Exception {
        crear(token, Map.of("nombre", "Limpieza", "duracionMin", 45, "orden", 2));
        crear(token, Map.of("nombre", "Consulta", "duracionMin", 30, "orden", 1));
        String blanqueamiento = idDe(crear(token, Map.of("nombre", "Blanqueamiento", "duracionMin", 60, "orden", 3)));
        accion(token, blanqueamiento, "desactivar");

        listar(token, "").andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nombre", contains("Consulta", "Limpieza")));
        listar(token, "?incluirInactivas=true")
                .andExpect(jsonPath("$[*].nombre", contains("Consulta", "Limpieza", "Blanqueamiento")));
    }

    @Test
    void conElMismoOrdenOrdenaPorNombre() throws Exception {
        crear(token, Map.of("nombre", "Limpieza", "duracionMin", 45, "orden", 1));
        crear(token, Map.of("nombre", "Consulta", "duracionMin", 30, "orden", 1));

        listar(token, "").andExpect(jsonPath("$[*].nombre", contains("Consulta", "Limpieza")));
    }

    // --- Auditoría ---

    @Test
    void altaAuditada() throws Exception {
        String id = idDe(crear(token, Map.of("nombre", "Limpieza", "duracionMin", 45)));

        Map<String, Object> evento = unicoEvento("PRESTACION_CREADA");
        assertThat(evento).containsEntry("entidad", "tipo_prestacion").containsEntry("entidad_id", UUID.fromString(id))
                .containsEntry("actor_id", cuenta.usuario()).containsEntry("valor_anterior", null);
        assertThat(json.readTree(evento.get("valor_nuevo").toString()))
                .isEqualTo(json.readTree("{\"nombre\": \"Limpieza\", \"duracion_min\": 45, \"visible_publica\": true,"
                        + " \"orden\": 1, \"activa\": true}"));
    }

    @Test
    void edicionAuditadaSoloConLoQueCambio() throws Exception {
        String id = idDe(crear(token, Map.of("nombre", "Limpieza", "duracionMin", 45)));
        editar(token, id, Map.of("nombre", "Limpieza", "duracionMin", 60, "visiblePublica", true, "orden", 1));

        Map<String, Object> evento = unicoEvento("PRESTACION_CAMBIADA");
        assertThat(json.readTree(evento.get("valor_anterior").toString())).isEqualTo(json.readTree("{\"duracion_min\": 45}"));
        assertThat(json.readTree(evento.get("valor_nuevo").toString())).isEqualTo(json.readTree("{\"duracion_min\": 60}"));
    }

    @Test
    void edicionIdenticaSinEvento() throws Exception {
        String id = idDe(crear(token, Map.of("nombre", "Limpieza", "duracionMin", 45)));
        editar(token, id, Map.of("nombre", "Limpieza", "duracionMin", 45, "visiblePublica", true, "orden", 1))
                .andExpect(status().isOk());

        assertThat(eventos("PRESTACION_CAMBIADA")).isEmpty();
    }

    @Test
    void desactivacionYActivacionAuditadas() throws Exception {
        String id = idDe(crear(token, Map.of("nombre", "Limpieza", "duracionMin", 45)));
        accion(token, id, "desactivar");
        accion(token, id, "desactivar");
        accion(token, id, "activar");

        Map<String, Object> desactivada = unicoEvento("PRESTACION_DESACTIVADA");
        assertThat(json.readTree(desactivada.get("valor_nuevo").toString())).isEqualTo(json.readTree("{\"activa\": false}"));
        Map<String, Object> activada = unicoEvento("PRESTACION_ACTIVADA");
        assertThat(json.readTree(activada.get("valor_nuevo").toString())).isEqualTo(json.readTree("{\"activa\": true}"));
    }

    // --- Aislamiento ---

    @Test
    void unaPrestacionAjenaResponde404SinModificarse() throws Exception {
        Cuenta otra = CuentasDePrueba.crear(jdbc);
        String deB = idDe(crear(tokenDe(otra), Map.of("nombre", "Limpieza", "duracionMin", 45)));

        consultar(token, deB).andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
        editar(token, deB, Map.of("nombre", "Cambiada", "duracionMin", 60, "visiblePublica", false, "orden", 9))
                .andExpect(status().isNotFound());
        accion(token, deB, "desactivar").andExpect(status().isNotFound());
        accion(token, deB, "activar").andExpect(status().isNotFound());

        assertThat(jdbc.queryForMap("SELECT nombre, duracion_min, activa FROM tipo_prestacion WHERE id = ?",
                UUID.fromString(deB)))
                .containsEntry("nombre", "Limpieza").containsEntry("duracion_min", 45).containsEntry("activa", true);
        listar(token, "?incluirInactivas=true").andExpect(jsonPath("$[*].id", not(hasItem(deB))));
    }

    private ResultActions crear(String conToken, Map<String, Object> cuerpo) throws Exception {
        return mvc.perform(conCuerpo(post(RUTA), conToken, cuerpo));
    }

    private ResultActions editar(String conToken, String id, Map<String, Object> cuerpo) throws Exception {
        return mvc.perform(conCuerpo(put(RUTA + "/" + id), conToken, cuerpo));
    }

    private ResultActions accion(String conToken, String id, String accion) throws Exception {
        return mvc.perform(conToken(post(RUTA + "/" + id + "/" + accion), conToken));
    }

    private ResultActions consultar(String conToken, String id) throws Exception {
        return mvc.perform(conToken(get(RUTA + "/" + id), conToken));
    }

    private ResultActions listar(String conToken, String query) throws Exception {
        return mvc.perform(conToken(get(RUTA + query), conToken));
    }

    private MockHttpServletRequestBuilder conCuerpo(MockHttpServletRequestBuilder pedido, String conToken,
            Map<String, Object> cuerpo) throws Exception {
        return conToken(pedido, conToken).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo));
    }

    private static MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder pedido, String conToken) {
        return pedido.header(HttpHeaders.AUTHORIZATION, "Bearer " + conToken);
    }

    private String idDe(ResultActions respuesta) throws Exception {
        return json.readTree(respuesta.andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    private Map<String, Object> unicoEvento(String accion) {
        List<Map<String, Object>> eventos = eventos(accion);
        assertThat(eventos).hasSize(1);
        return eventos.get(0);
    }

    private List<Map<String, Object>> eventos(String accion) {
        return jdbc.queryForList("""
                SELECT entidad, entidad_id, actor_id, valor_anterior::text AS valor_anterior,
                       valor_nuevo::text AS valor_nuevo
                FROM evento_auditoria WHERE consultorio_id = ? AND accion = ? ORDER BY ocurrido_at""",
                cuenta.consultorio(), accion);
    }

    private static String tokenDe(Cuenta cuenta) {
        return TokensDePrueba.valido(SECRETO_JWT_DE_PRUEBA, cuenta.usuario(), cuenta.consultorio(), 1);
    }
}
