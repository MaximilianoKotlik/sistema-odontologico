package ar.sistemaodontologico.agenda.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import ar.sistemaodontologico.compartido.IntegracionPostgresTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pruebas.agenda.AgendaDePrueba;
import pruebas.seguridad.TokensDePrueba;

/**
 * Base de los tests de la API de agenda (C-12): MockMvc con el JWT del odontólogo y PostgreSQL real (regla dura 8).
 * Cada test arma su consultorio con {@link AgendaDePrueba}; {@link #otraAgenda()} da el consultorio B para los tests
 * de aislamiento (regla dura 6). Datos ficticios (regla dura 17).
 */
@AutoConfigureMockMvc
abstract class ApiDeAgendaTest extends IntegracionPostgresTest {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected ObjectMapper json;

    protected AgendaDePrueba agenda;
    protected String token;

    @BeforeEach
    void crearAgenda() {
        agenda = AgendaDePrueba.nueva(jdbc);
        token = tokenDe(agenda);
    }

    protected AgendaDePrueba otraAgenda() {
        return AgendaDePrueba.nueva(jdbc);
    }

    protected static String tokenDe(AgendaDePrueba de) {
        return TokensDePrueba.valido(SECRETO_JWT_DE_PRUEBA, de.usuario(), de.consultorio(), 1);
    }

    protected ResultActions pedirGet(String ruta) throws Exception {
        return mvc.perform(conToken(get(ruta), token));
    }

    protected ResultActions pedirPost(String ruta, Object cuerpo) throws Exception {
        return mvc.perform(conCuerpo(post(ruta), token, cuerpo));
    }

    protected ResultActions pedirPatch(String ruta, Object cuerpo) throws Exception {
        return mvc.perform(conCuerpo(patch(ruta), token, cuerpo));
    }

    protected ResultActions pedirDelete(String ruta) throws Exception {
        return mvc.perform(conToken(delete(ruta), token));
    }

    protected MockHttpServletRequestBuilder conCuerpo(MockHttpServletRequestBuilder pedido, String conToken,
            Object cuerpo) throws Exception {
        return conToken(pedido, conToken).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(cuerpo));
    }

    protected static MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder pedido, String conToken) {
        return pedido.header(HttpHeaders.AUTHORIZATION, "Bearer " + conToken);
    }

    protected JsonNode cuerpo(ResultActions respuesta) throws Exception {
        return json.readTree(respuesta.andReturn().getResponse().getContentAsString());
    }

    /** Las acciones de los eventos de auditoría de esa entidad, en orden. */
    protected List<String> accionesAuditadas(UUID entidad) {
        return jdbc.queryForList("SELECT accion FROM evento_auditoria WHERE entidad_id = ? ORDER BY ocurrido_at",
                String.class, entidad);
    }

    protected Map<String, Object> filaDeTurno(UUID turno) {
        return jdbc.queryForMap("SELECT * FROM turno WHERE id = ?", turno);
    }
}
