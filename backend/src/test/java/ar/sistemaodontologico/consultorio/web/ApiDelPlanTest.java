package ar.sistemaodontologico.consultorio.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import ar.sistemaodontologico.compartido.IntegracionPostgresTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pruebas.agenda.AgendaDePrueba;
import pruebas.reloj.ConfiguracionDeRelojDePrueba;
import pruebas.reloj.RelojDePrueba;
import pruebas.seguridad.TokensDePrueba;

/**
 * Base de los tests HTTP del plan (C-20): MockMvc, PostgreSQL real y el {@link RelojDePrueba}, que no avanza solo. Usa
 * la misma configuración que {@code ApiPublicaTest} para compartir el contexto de Spring. Datos ficticios (regla dura 17).
 */
@AutoConfigureMockMvc
@Import(ConfiguracionDeRelojDePrueba.class)
public abstract class ApiDelPlanTest extends IntegracionPostgresTest {

    protected static final ZoneId BUENOS_AIRES = ZoneId.of("America/Argentina/Buenos_Aires");

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected ObjectMapper json;

    @Autowired
    protected RelojDePrueba reloj;

    protected AgendaDePrueba agenda;
    protected String token;

    @BeforeEach
    void crearConsultorio() {
        agenda = AgendaDePrueba.nueva(jdbc);
        token = tokenDe(agenda);
    }

    protected static String tokenDe(AgendaDePrueba de) {
        return TokensDePrueba.valido(SECRETO_JWT_DE_PRUEBA, de.usuario(), de.consultorio(), 1);
    }

    /** Hoy en Buenos Aires según el reloj de prueba. */
    protected LocalDate hoy() {
        return reloj.instant().atZone(BUENOS_AIRES).toLocalDate();
    }

    protected void plan(AgendaDePrueba de, String plan, LocalDate pruebaHasta) {
        jdbc.update("UPDATE consultorio SET plan = ?, prueba_hasta = ? WHERE id = ?", plan, pruebaHasta,
                de.consultorio());
    }

    /** Prueba vencida ayer, que la tarea diaria todavía no pasó a {@code VENCIDO}. */
    protected void pruebaVencidaAyer(AgendaDePrueba de) {
        plan(de, "PRUEBA", hoy().minusDays(1));
    }

    protected ResultActions pedir(MockHttpServletRequestBuilder pedido) throws Exception {
        return mvc.perform(pedido.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    protected ResultActions consultarPlan() throws Exception {
        return pedir(get("/api/v1/plan"));
    }

    protected List<String> accionesDelConsultorio(AgendaDePrueba de) {
        return jdbc.queryForList("SELECT accion FROM evento_auditoria WHERE consultorio_id = ? ORDER BY id",
                String.class, de.consultorio());
    }

    protected UUID consultorio() {
        return agenda.consultorio();
    }
}
