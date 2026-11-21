package ar.sistemaodontologico.reserva.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.sistemaodontologico.compartido.IntegracionPostgresTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import pruebas.agenda.AgendaDePrueba;
import pruebas.reloj.ConfiguracionDeRelojDePrueba;
import pruebas.reloj.RelojDePrueba;

/**
 * Base de los tests de la API pública (C-15): un consultorio con atención de lunes a viernes de 09:00 a 13:00 y una
 * prestación de 30 minutos. Los horarios se toman de un lunes futuro, relativo al reloj. Datos ficticios (regla dura 17).
 */
@AutoConfigureMockMvc
@Import(ConfiguracionDeRelojDePrueba.class)
abstract class ApiPublicaTest extends IntegracionPostgresTest {

    protected static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");
    protected static final String TELEFONO_CONSULTORIO = "+5491100000010";
    protected static final String RUTA_CONSULTORIOS = "/api/v1/public/consultorios/";
    protected static final String RUTA_TURNOS = "/api/v1/public/turnos/";

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected ObjectMapper json;

    @Autowired
    protected RelojDePrueba reloj;

    protected AgendaDePrueba agenda;
    protected String slug;
    protected UUID consulta;

    @BeforeEach
    void crearConsultorioPublico() {
        agenda = AgendaDePrueba.nueva(jdbc);
        slug = prepararPublico(agenda);
        consulta = agenda.prestacion("Consulta", 30, true);
    }

    protected String prepararPublico(AgendaDePrueba otra) {
        jdbc.update("UPDATE consultorio SET direccion = 'Calle Falsa 123', telefono_contacto = ? WHERE id = ?",
                TELEFONO_CONSULTORIO, otra.consultorio());
        return jdbc.queryForObject("SELECT slug FROM consultorio WHERE id = ?", String.class, otra.consultorio());
    }

    /** El primer lunes que está al menos a 7 días del reloj: dentro del horizonte y fuera de toda anticipación. */
    protected LocalDate lunes() {
        LocalDate base = reloj.instant().atZone(ZONA).toLocalDate().plusDays(7);
        return base.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    }

    protected Instant lunesA(String hora) {
        return lunes().atTime(LocalTime.parse(hora)).atZone(ZONA).toInstant();
    }

    protected Map<String, Object> pedido(Instant inicio, String dni) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("prestacionId", consulta.toString());
        cuerpo.put("inicio", inicio.toString());
        cuerpo.put("nombre", "Paciente");
        cuerpo.put("apellido", "Prueba");
        cuerpo.put("dni", dni);
        cuerpo.put("telefono", "+54 9 11 0000-0001");
        cuerpo.put("aceptaPrivacidad", true);
        return cuerpo;
    }

    protected ResultActions reservar(String enSlug, Map<String, Object> cuerpo) throws Exception {
        return mvc.perform(post(RUTA_CONSULTORIOS + enSlug + "/turnos").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(cuerpo)));
    }

    /** Reserva y devuelve el token del link de gestión. */
    protected String reservarYObtenerToken(Instant inicio) throws Exception {
        String respuesta = reservar(slug, pedido(inicio, AgendaDePrueba.dniNuevo())).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(respuesta).path("linkGestion").asText().substring("/t/".length());
    }

    protected UUID turnoDe(String token) {
        return jdbc.queryForObject("""
                SELECT turno_id FROM token_gestion_turno
                 WHERE token_hash = encode(sha256(convert_to(?, 'UTF8')), 'hex')""", UUID.class, token);
    }

    protected ResultActions ver(String token) throws Exception {
        return mvc.perform(get(RUTA_TURNOS + token));
    }

    protected ResultActions cancelar(String token) throws Exception {
        return mvc.perform(post(RUTA_TURNOS + token + "/cancelacion"));
    }

    protected ResultActions reprogramar(String token, Instant nuevoInicio) throws Exception {
        return mvc.perform(post(RUTA_TURNOS + token + "/reprogramacion").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("nuevoInicio", nuevoInicio.toString()))));
    }

    protected JsonNode cuerpo(ResultActions resultado) throws Exception {
        return json.readTree(resultado.andReturn().getResponse().getContentAsString());
    }

    protected List<String> huecosDe(JsonNode dia) {
        return json.convertValue(dia.path("huecos"), json.getTypeFactory().constructCollectionType(List.class, String.class));
    }

    protected List<String> acciones() {
        return jdbc.queryForList("SELECT accion FROM evento_auditoria WHERE consultorio_id = ? ORDER BY id", String.class,
                agenda.consultorio());
    }

    protected void desactivarReserva() {
        jdbc.update("UPDATE consultorio SET reserva_publica_activa = false WHERE id = ?", agenda.consultorio());
    }

    protected void vencerPlan() {
        jdbc.update("UPDATE consultorio SET plan = 'VENCIDO' WHERE id = ?", agenda.consultorio());
    }

    /** Deja el turno a 20 horas, con la anticipación de 24 h por defecto: ya no se puede modificar. */
    protected void plazoVencidoPara(String token) {
        long grilla = Duration.ofMinutes(5).toSeconds();
        long segundos = reloj.instant().plus(Duration.ofHours(20)).getEpochSecond() / grilla * grilla;
        Instant inicio = Instant.ofEpochSecond(segundos);
        jdbc.update("UPDATE turno SET inicio = ?, fin = ? WHERE id = ?", java.sql.Timestamp.from(inicio),
                java.sql.Timestamp.from(inicio.plus(Duration.ofMinutes(30))), turnoDe(token));
    }
}
