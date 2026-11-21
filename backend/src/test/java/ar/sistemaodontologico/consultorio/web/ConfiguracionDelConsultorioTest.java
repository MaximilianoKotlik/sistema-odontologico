package ar.sistemaodontologico.consultorio.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.sistemaodontologico.compartido.IntegracionPostgresTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import pruebas.seguridad.CuentasDePrueba;
import pruebas.seguridad.CuentasDePrueba.Cuenta;
import pruebas.seguridad.TokensDePrueba;

/**
 * {@code GET/PUT /api/v1/consultorio} (C-08, tareas 3.2 a 3.5; specs configuracion-consultorio y el delta de
 * slug-consultorio). Datos ficticios (regla dura 17).
 */
@AutoConfigureMockMvc
class ConfiguracionDelConsultorioTest extends IntegracionPostgresTest {

    private static final String RUTA = "/api/v1/consultorio";

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
        token = TokensDePrueba.valido(SECRETO_JWT_DE_PRUEBA, cuenta.usuario(), cuenta.consultorio(), 1);
    }

    // --- Consulta ---

    @Test
    void consultaConLosValoresPorDefecto() throws Exception {
        mvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Consultorio Demo"))
                .andExpect(jsonPath("$.slug").value(slugDe(cuenta.consultorio())))
                .andExpect(jsonPath("$.zonaHoraria").value("America/Argentina/Buenos_Aires"))
                .andExpect(jsonPath("$.anticipacionCancelacionMin").value(1440))
                .andExpect(jsonPath("$.anticipacionReservaMin").value(120))
                .andExpect(jsonPath("$.horizonteReservaDias").value(60))
                .andExpect(jsonPath("$.pasoHuecosMin").value(15))
                .andExpect(jsonPath("$.reservaPublicaActiva").value(true))
                .andExpect(jsonPath("$.plan").value("PRUEBA"));
    }

    @Test
    void sinSesionResponde401() throws Exception {
        mvc.perform(get(RUTA)).andExpect(status().isUnauthorized());
    }

    // --- Edición ---

    @Test
    void cambioValido() throws Exception {
        guardar(valido().put("anticipacionCancelacionMin", 2880).put("pasoHuecosMin", 30))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anticipacionCancelacionMin").value(2880))
                .andExpect(jsonPath("$.pasoHuecosMin").value(30));

        mvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.anticipacionCancelacionMin").value(2880))
                .andExpect(jsonPath("$.pasoHuecosMin").value(30));
    }

    @Test
    void elPlanYLaPruebaSeIgnoran() throws Exception {
        guardar(valido().put("plan", "ACTIVO").put("pruebaHasta", "2099-01-01")).andExpect(status().isOk());

        assertThat(jdbc.queryForMap("SELECT plan, prueba_hasta FROM consultorio WHERE id = ?", cuenta.consultorio()))
                .containsEntry("plan", "PRUEBA")
                .containsEntry("prueba_hasta", null);
    }

    @Test
    void pasoDeHuecosNoPermitido() throws Exception {
        guardar(valido().put("pasoHuecosMin", 25))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value("pasoHuecosMin"));
    }

    @Test
    void horizonteMayorA365Dias() throws Exception {
        guardar(valido().put("horizonteReservaDias", 366))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value("horizonteReservaDias"));
    }

    @Test
    void nombreObligatorio() throws Exception {
        guardar(valido().put("nombre", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("nombre"));
    }

    @Test
    void anticipacionDeReservaQueNoEntraEnElHorizonte() throws Exception {
        guardar(valido().put("horizonteReservaDias", 1).put("anticipacionReservaMin", 1440))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("ANTICIPACION_FUERA_DE_HORIZONTE"))
                .andExpect(jsonPath("$.campo").value("anticipacionReservaMin"));
    }

    @Test
    void telefonoLocalNormalizado() throws Exception {
        guardar(valido().put("telefonoContacto", "011 15-0000-0001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telefonoContacto").value("+5491100000001"));
    }

    @Test
    void telefonoInvalido() throws Exception {
        guardar(valido().put("telefonoContacto", "123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("TELEFONO_INVALIDO"));
    }

    @Test
    void telefonoObligatorio() throws Exception {
        // Decisión de Maxi en #15 (mockups de C-09): el asistente exige el teléfono de contacto.
        guardar(valido().put("telefonoContacto", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value("telefonoContacto"));
        guardar(valido().putNull("telefonoContacto"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("telefonoContacto"));
    }

    @Test
    void zonaHorariaInexistente() throws Exception {
        guardar(valido().put("zonaHoraria", "America/Ciudad_Inventada"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("ZONA_HORARIA_INVALIDA"));
    }

    // --- Slug ---

    @Test
    void slugLibre() throws Exception {
        String libre = "dra-" + sufijo();

        guardar(valido().put("slug", libre)).andExpect(status().isOk()).andExpect(jsonPath("$.slug").value(libre));
    }

    @Test
    void slugReservado() throws Exception {
        guardar(valido().put("slug", "admin"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SLUG_RESERVADO"));
    }

    @Test
    void slugEnUsoConSugerencias() throws Exception {
        Cuenta otra = CuentasDePrueba.crear(jdbc);
        String tomado = slugDe(otra.consultorio());

        guardar(valido().put("slug", tomado))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SLUG_EN_USO"))
                .andExpect(jsonPath("$.sugerencias[0]").value(tomado + "-2"));
    }

    @Test
    void elSlugAnteriorQuedaLibre() throws Exception {
        String anterior = slugDe(cuenta.consultorio());
        guardar(valido().put("slug", "norte-" + sufijo())).andExpect(status().isOk());

        Cuenta otra = CuentasDePrueba.crear(jdbc);
        String tokenDeOtra = TokensDePrueba.valido(SECRETO_JWT_DE_PRUEBA, otra.usuario(), otra.consultorio(), 1);
        ObjectNode pedido = valido().put("slug", anterior);
        mvc.perform(put(RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeOtra)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(pedido)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value(anterior));
    }

    @Test
    void elMismoSlugNoChocaConsigo() throws Exception {
        guardar(valido()).andExpect(status().isOk());
    }

    // --- Auditoría ---

    @Test
    void anticipacionAuditadaSoloConElCampoCambiado() throws Exception {
        // El primer guardado carga el teléfono (el registro lo deja vacío); el segundo es el que se mira.
        guardar(valido()).andExpect(status().isOk());
        guardar(valido().put("anticipacionCancelacionMin", 2880)).andExpect(status().isOk());

        List<Map<String, Object>> eventos = eventos();
        assertThat(eventos).hasSize(2);
        Map<String, Object> evento = eventos.get(1);
        assertThat(evento)
                .containsEntry("accion", "CONSULTORIO_CONFIG_CAMBIADA")
                .containsEntry("entidad", "consultorio")
                .containsEntry("entidad_id", cuenta.consultorio())
                .containsEntry("actor_tipo", "ODONTOLOGO")
                .containsEntry("actor_id", cuenta.usuario());
        assertThat(json.readTree(evento.get("valor_anterior").toString()))
                .isEqualTo(json.readTree("{\"anticipacion_cancelacion_min\": 1440}"));
        assertThat(json.readTree(evento.get("valor_nuevo").toString()))
                .isEqualTo(json.readTree("{\"anticipacion_cancelacion_min\": 2880}"));
        assertThat(evento.get("ip")).isNotNull();
    }

    @Test
    void guardadoIdenticoSinEvento() throws Exception {
        guardar(valido()).andExpect(status().isOk());
        int despuesDelPrimero = eventos().size();

        guardar(valido()).andExpect(status().isOk());

        assertThat(eventos()).hasSize(despuesDelPrimero);
    }

    @Test
    void unaValidacionFallidaNoAudita() throws Exception {
        guardar(valido().put("zonaHoraria", "America/Ciudad_Inventada")).andExpect(status().isBadRequest());

        assertThat(eventos()).isEmpty();
    }

    // --- Aislamiento ---

    @Test
    void unBodyConOtroConsultorioSoloCambiaElDeLaSesion() throws Exception {
        Cuenta otra = CuentasDePrueba.crear(jdbc);

        guardar(valido().put("id", otra.consultorio().toString()).put("consultorioId", otra.consultorio().toString())
                .put("nombre", "Consultorio Cambiado"))
                .andExpect(status().isOk());

        assertThat(nombreDe(cuenta.consultorio())).isEqualTo("Consultorio Cambiado");
        assertThat(nombreDe(otra.consultorio())).isEqualTo("Consultorio Demo");
    }

    private ResultActions guardar(ObjectNode pedido) throws Exception {
        return mvc.perform(put(RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(pedido)));
    }

    /** La configuración por defecto de la cuenta con un teléfono válido, lista para modificar un campo. */
    private ObjectNode valido() {
        ObjectNode pedido = json.createObjectNode();
        pedido.put("nombre", "Consultorio Demo");
        pedido.put("slug", slugDe(cuenta.consultorio()));
        pedido.putNull("direccion");
        pedido.put("telefonoContacto", "+5491100000001");
        pedido.put("zonaHoraria", "America/Argentina/Buenos_Aires");
        pedido.put("anticipacionCancelacionMin", 1440);
        pedido.put("anticipacionReservaMin", 120);
        pedido.put("horizonteReservaDias", 60);
        pedido.put("pasoHuecosMin", 15);
        pedido.put("reservaPublicaActiva", true);
        return pedido;
    }

    private List<Map<String, Object>> eventos() {
        return jdbc.queryForList("""
                SELECT accion, entidad, entidad_id, actor_tipo, actor_id, valor_anterior::text AS valor_anterior,
                       valor_nuevo::text AS valor_nuevo, ip::text AS ip
                FROM evento_auditoria WHERE consultorio_id = ? AND accion = 'CONSULTORIO_CONFIG_CAMBIADA'
                ORDER BY ocurrido_at""", cuenta.consultorio());
    }

    private String slugDe(UUID consultorio) {
        return jdbc.queryForObject("SELECT slug FROM consultorio WHERE id = ?", String.class, consultorio);
    }

    private String nombreDe(UUID consultorio) {
        return jdbc.queryForObject("SELECT nombre FROM consultorio WHERE id = ?", String.class, consultorio);
    }

    private static String sufijo() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
