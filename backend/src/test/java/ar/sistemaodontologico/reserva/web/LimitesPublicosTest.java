package ar.sistemaodontologico.reserva.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import pruebas.agenda.AgendaDePrueba;

/** Cupos de la API pública por IP y por token (C-15, tareas 8.1 y 8.2; spec seguridad-http). */
@TestPropertySource(properties = {
    "app.rate-limit-publico.reserva.capacidad=2", "app.rate-limit-publico.reserva.periodo=1h",
    "app.rate-limit-publico.consulta.capacidad=3", "app.rate-limit-publico.consulta.periodo=1m",
    "app.rate-limit-publico.token.capacidad=2", "app.rate-limit-publico.token.periodo=1h"})
class LimitesPublicosTest extends ApiPublicaTest {

    private static final AtomicInteger OCTETO = new AtomicInteger(1);
    private static final String TOKEN_INEXISTENTE = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB";

    private String ip;

    @BeforeEach
    void ipPropia() {
        ip = otraIp();
    }

    @Test
    void rafagaDeReservasDesdeUnaIp() throws Exception {
        reservarDesde(ip, "09:00").andExpect(status().isCreated());
        reservarDesde(ip, "10:00").andExpect(status().isCreated());

        reservarDesde(ip, "11:00").andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.codigo").value("DEMASIADAS_SOLICITUDES"))
                .andExpect(header().exists("Retry-After"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM turno WHERE consultorio_id = ?", Long.class,
                agenda.consultorio())).isEqualTo(2);
    }

    @Test
    void otraIpTieneSuPropioCupo() throws Exception {
        reservarDesde(ip, "09:00");
        reservarDesde(ip, "10:00");

        reservarDesde(otraIp(), "11:00").andExpect(status().isCreated());
    }

    @Test
    void rafagaDeConsultas() throws Exception {
        for (int i = 0; i < 3; i++) {
            consultar(ip).andExpect(status().isOk());
        }

        consultar(ip).andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
    }

    @Test
    void lasModificacionesSeLimitanPorTokenAunDesdeIpsDistintas() throws Exception {
        String token = reservarDesdeYObtenerToken(otraIp());

        reprogramarDesde(otraIp(), token).andExpect(status().isOk());
        reprogramarDesde(otraIp(), token).andExpect(status().isOk());

        reprogramarDesde(otraIp(), token).andExpect(status().isTooManyRequests());
    }

    @Test
    void lasLecturasNoGastanElCupoDelToken() throws Exception {
        String token = reservarDesdeYObtenerToken(otraIp());
        for (int i = 0; i < 3; i++) {
            mvc.perform(get(RUTA_TURNOS + token).with(desde(otraIp()))).andExpect(status().isOk());
        }

        reprogramarDesde(otraIp(), token).andExpect(status().isOk());
    }

    @Test
    void tokensInexistentesDesdeUnaIpRecibenElMismo404HastaElLimite() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(get(RUTA_TURNOS + TOKEN_INEXISTENTE).with(desde(ip))).andExpect(status().isNotFound());
        }

        mvc.perform(get(RUTA_TURNOS + TOKEN_INEXISTENTE).with(desde(ip))).andExpect(status().isTooManyRequests());
    }

    @Test
    void probarTokensPorPostTambienGastaElCupoDeLaIp() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(post(RUTA_TURNOS + tokenDistinto(i) + "/cancelacion").with(desde(ip)))
                    .andExpect(status().isNotFound());
        }

        mvc.perform(post(RUTA_TURNOS + tokenDistinto(9) + "/cancelacion").with(desde(ip)))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void cuerpoEnorme() throws Exception {
        String relleno = "x".repeat(100 * 1024);

        mvc.perform(post(RUTA_CONSULTORIOS + slug + "/turnos").with(desde(ip)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("nombre", relleno)))).andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.codigo").value("CUERPO_DEMASIADO_GRANDE"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM turno WHERE consultorio_id = ?", Long.class,
                agenda.consultorio())).isZero();
    }

    @Test
    void elCuatroVeintinueveNoTieneElToken() throws Exception {
        String token = reservarDesdeYObtenerToken(otraIp());
        String otra = otraIp();
        reprogramarDesde(otra, token);
        reprogramarDesde(otra, token);

        reprogramarDesde(otra, token).andExpect(status().isTooManyRequests())
                .andExpect(header().string("Cache-Control", Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.instance", Matchers.not(Matchers.containsString(token))));
    }

    private static String tokenDistinto(int i) {
        return "C".repeat(42) + (char) ('a' + i);
    }

    private ResultActions consultar(String desdeIp) throws Exception {
        return mvc.perform(get(RUTA_CONSULTORIOS + slug).with(desde(desdeIp)));
    }

    private ResultActions reservarDesde(String desdeIp, String hora) throws Exception {
        return mvc.perform(post(RUTA_CONSULTORIOS + slug + "/turnos").with(desde(desdeIp))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(pedido(lunesA(hora), AgendaDePrueba.dniNuevo()))));
    }

    private String reservarDesdeYObtenerToken(String desdeIp) throws Exception {
        String respuesta = reservarDesde(desdeIp, "09:00").andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString();
        return json.readTree(respuesta).path("linkGestion").asText().substring(3);
    }

    private ResultActions reprogramarDesde(String desdeIp, String token) throws Exception {
        String destino = minutos.getAndAdd(30) % 2 == 0 ? "10:00" : "11:00";
        return mvc.perform(post(RUTA_TURNOS + token + "/reprogramacion").with(desde(desdeIp))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("nuevoInicio", lunesA(destino).toString()))));
    }

    private final AtomicInteger minutos = new AtomicInteger();

    private static String otraIp() {
        int n = OCTETO.getAndIncrement();
        return "198.51." + (n / 250) + "." + (n % 250 + 1);
    }

    private static RequestPostProcessor desde(String ip) {
        return peticion -> {
            peticion.setRemoteAddr(ip);
            return peticion;
        };
    }
}
