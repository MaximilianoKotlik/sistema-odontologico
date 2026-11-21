package ar.sistemaodontologico.consultorio.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.sistemaodontologico.compartido.IntegracionPostgresTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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

/** {@code GET/PUT /api/v1/disponibilidad} (C-08, tareas 5.2, 5.4 y 5.5; spec disponibilidad-semanal). */
@AutoConfigureMockMvc
class DisponibilidadTest extends IntegracionPostgresTest {

    private static final String RUTA = "/api/v1/disponibilidad";

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

    @Test
    void grillaVacia() throws Exception {
        consultar(token).andExpect(status().isOk()).andExpect(jsonPath("$.franjas", hasSize(0)));
    }

    @Test
    void lunesEnDosFranjas() throws Exception {
        guardar(token, franja(1, "15:00", "19:00"), franja(1, "09:00", "13:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.franjas", hasSize(2)));

        consultar(token)
                .andExpect(jsonPath("$.franjas[0].diaSemana").value(1))
                .andExpect(jsonPath("$.franjas[0].horaInicio").value("09:00"))
                .andExpect(jsonPath("$.franjas[0].horaFin").value("13:00"))
                .andExpect(jsonPath("$.franjas[1].horaInicio").value("15:00"))
                .andExpect(jsonPath("$.franjas[1].horaFin").value("19:00"));
    }

    @Test
    void reemplazoCompleto() throws Exception {
        guardar(token, franja(1, "09:00", "13:00"), franja(2, "09:00", "13:00"));

        guardar(token, franja(5, "10:00", "12:00")).andExpect(status().isOk());

        consultar(token).andExpect(jsonPath("$.franjas", hasSize(1))).andExpect(jsonPath("$.franjas[0].diaSemana").value(5));
    }

    @Test
    void grillaVaciaGuardada() throws Exception {
        guardar(token, franja(1, "09:00", "13:00"));

        guardar(token).andExpect(status().isOk());

        consultar(token).andExpect(jsonPath("$.franjas", hasSize(0)));
    }

    @Test
    void unaFranjaInvalidaNoDejaCambiosParciales() throws Exception {
        guardar(token, franja(1, "09:00", "13:00"));

        guardar(token, franja(2, "09:00", "13:00"), franja(3, "10:00", "09:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("FRANJA_INVALIDA"));

        consultar(token).andExpect(jsonPath("$.franjas", hasSize(1))).andExpect(jsonPath("$.franjas[0].diaSemana").value(1));
    }

    @Test
    void horaNoMultiploDe5() throws Exception {
        guardar(token, franja(1, "09:03", "12:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("FRANJA_INVALIDA"));
    }

    @Test
    void solapadasElMismoDia() throws Exception {
        guardar(token, franja(1, "09:00", "13:00"), franja(1, "12:00", "15:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("FRANJAS_SOLAPADAS"))
                .andExpect(jsonPath("$.dias", hasItem(1)));
    }

    @Test
    void contiguasYMismoHorarioEnDiasDistintos() throws Exception {
        guardar(token, franja(1, "09:00", "13:00"), franja(1, "13:00", "15:00"), franja(2, "09:00", "13:00"))
                .andExpect(status().isOk());
    }

    @Test
    void franjaIncompleta() throws Exception {
        mvc.perform(put(RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"franjas\": [{\"diaSemana\": 1, \"horaInicio\": \"09:00\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    // --- Auditoría ---

    @Test
    void grillaCambiadaAuditadaCompleta() throws Exception {
        guardar(token, franja(1, "09:00", "13:00"));
        guardar(token, franja(1, "09:00", "12:00"));

        List<Map<String, Object>> eventos = eventos();
        assertThat(eventos).hasSize(2);
        Map<String, Object> evento = eventos.get(1);
        assertThat(evento).containsEntry("entidad", "disponibilidad").containsEntry("entidad_id", cuenta.consultorio())
                .containsEntry("actor_id", cuenta.usuario());
        assertThat(json.readTree(evento.get("valor_anterior").toString())).isEqualTo(json.readTree(
                "{\"franjas\": [{\"dia_semana\": 1, \"hora_inicio\": \"09:00\", \"hora_fin\": \"13:00\"}]}"));
        assertThat(json.readTree(evento.get("valor_nuevo").toString())).isEqualTo(json.readTree(
                "{\"franjas\": [{\"dia_semana\": 1, \"hora_inicio\": \"09:00\", \"hora_fin\": \"12:00\"}]}"));
    }

    @Test
    void laMismaGrillaEnOtroOrdenNoAudita() throws Exception {
        guardar(token, franja(1, "09:00", "13:00"), franja(2, "09:00", "13:00"));

        guardar(token, franja(2, "09:00", "13:00"), franja(1, "09:00", "13:00")).andExpect(status().isOk());

        assertThat(eventos()).hasSize(1);
    }

    // --- Aislamiento ---

    @Test
    void otroConsultorioIntacto() throws Exception {
        Cuenta otra = CuentasDePrueba.crear(jdbc);
        String tokenDeOtra = tokenDe(otra);
        guardar(tokenDeOtra, franja(1, "09:00", "13:00"));
        guardar(token, franja(2, "09:00", "13:00"));

        guardar(token).andExpect(status().isOk());

        consultar(tokenDeOtra).andExpect(jsonPath("$.franjas", hasSize(1)))
                .andExpect(jsonPath("$.franjas[0].diaSemana").value(1));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM disponibilidad WHERE consultorio_id = ?", Integer.class,
                otra.consultorio())).isEqualTo(1);
    }

    private ResultActions guardar(String conToken, Map<?, ?>... franjas) throws Exception {
        String cuerpo = json.writeValueAsString(Map.of("franjas", Arrays.asList(franjas)));
        return mvc.perform(put(RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + conToken)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private ResultActions consultar(String conToken) throws Exception {
        return mvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + conToken));
    }

    private static Map<String, Object> franja(int dia, String inicio, String fin) {
        return Map.of("diaSemana", dia, "horaInicio", inicio, "horaFin", fin);
    }

    private List<Map<String, Object>> eventos() {
        return jdbc.queryForList("""
                SELECT entidad, entidad_id, actor_id, valor_anterior::text AS valor_anterior,
                       valor_nuevo::text AS valor_nuevo
                FROM evento_auditoria WHERE consultorio_id = ? AND accion = 'DISPONIBILIDAD_CAMBIADA'
                ORDER BY ocurrido_at""", cuenta.consultorio());
    }

    private static String tokenDe(Cuenta cuenta) {
        return TokensDePrueba.valido(SECRETO_JWT_DE_PRUEBA, cuenta.usuario(), cuenta.consultorio(), 1);
    }
}
