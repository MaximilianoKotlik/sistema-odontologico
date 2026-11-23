package ar.sistemaodontologico.consultorio.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static pruebas.agenda.AgendaDePrueba.lunesA;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Matriz de escrituras con el plan vencido (C-20, tarea 3.2; design D4 con las pautas de Maxi en #53; spec
 * solo-lectura-por-plan). Recorre todas las escrituras que registra Spring MVC, así un endpoint nuevo nace bloqueado y
 * el test falla si alguien lo deja abierto. Los tests de evasión van por HTTP real (Tomcat + firewall de Spring
 * Security), porque MockMvc no normaliza la URL como el servidor.
 */
class MatrizDeEscriturasTest extends ApiDelPlanTest {

    private static final Set<RequestMethod> ESCRITURAS =
            Set.of(RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE);
    private static final Pattern VARIABLE = Pattern.compile("\\{[^}]+}");

    /** Las únicas escrituras que siguen con el plan vencido (spec §Rutas permitidas). */
    private static boolean permitida(String metodo, String ruta) {
        return ruta.startsWith("/api/v1/public/")
                || ruta.startsWith("/api/v1/auth/")
                || metodo.equals("POST") && ruta.equals("/api/v1/exportacion")
                || metodo.equals("POST") && ruta.matches("^/api/v1/turnos/[^/]+/visto$");
    }

    record Escritura(String metodo, String patron) {

        String ruta() {
            return VARIABLE.matcher(patron).replaceAll(UUID.randomUUID().toString());
        }

        @Override
        public String toString() {
            return metodo + " " + patron;
        }
    }

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mapeos;

    @BeforeEach
    void vencer() {
        plan(agenda, "VENCIDO", hoy().minusDays(1));
    }

    @Test
    void todaEscrituraPrivadaResponde403SalvoLasPermitidas() throws Exception {
        List<Escritura> escrituras = escrituras();
        List<String> bloqueadas = new ArrayList<>();
        for (Escritura escritura : escrituras) {
            String ruta = escritura.ruta();
            MockHttpServletResponse respuesta = pedir(request(HttpMethod.valueOf(escritura.metodo()), ruta)
                    .header(HttpHeaders.ORIGIN, ORIGEN_FRONTEND)
                    .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andReturn().getResponse();
            String cuerpo = respuesta.getContentAsString();
            if (permitida(escritura.metodo(), escritura.patron())) {
                assertThat(cuerpo).as(escritura.toString()).doesNotContain("PLAN_VENCIDO");
            } else {
                assertThat(respuesta.getStatus()).as(escritura.toString()).isEqualTo(403);
                assertThat(cuerpo).as(escritura.toString()).contains("\"codigo\":\"PLAN_VENCIDO\"");
                bloqueadas.add(escritura.toString());
            }
        }

        // Las escrituras que nombra el spec (§Matriz de escrituras bloqueadas) están en la matriz.
        assertThat(bloqueadas).contains("POST /api/v1/turnos", "PATCH /api/v1/turnos/{id}/estado",
                "POST /api/v1/turnos/{id}/reprogramar", "PATCH /api/v1/turnos/{id}/duracion",
                "PATCH /api/v1/turnos/{id}/nota", "POST /api/v1/bloqueos", "DELETE /api/v1/bloqueos/{id}",
                "PUT /api/v1/consultorio", "PUT /api/v1/disponibilidad", "POST /api/v1/prestaciones",
                "PUT /api/v1/prestaciones/{id}", "POST /api/v1/prestaciones/{id}/desactivar",
                "POST /api/v1/prestaciones/{id}/activar",
                // C-17: la revisión del paciente también es una escritura (design, riesgo "Plan VENCIDO").
                "POST /api/v1/pacientes/{id}/confirmar-revision", "PATCH /api/v1/pacientes/{id}");
        // Ninguna escritura bloqueada audita. La exportación sigue permitida y registra su propio evento (C-19).
        assertThat(accionesDelConsultorio(agenda)).isSubsetOf("EXPORTACION_GENERADA");
    }

    @Test
    void ningunaRutaHttpCambiaElPlan() {
        assertThat(escrituras()).extracting(Escritura::patron).noneMatch(patron -> patron.contains("plan"));
    }

    @Nested
    class Evasion {

        @Autowired
        TestRestTemplate http;

        @LocalServerPort
        int puerto;

        private UUID turno;
        private UUID bloqueo;

        @BeforeEach
        void prepararTurnoYBloqueo() {
            UUID consulta = agenda.prestacion("Consulta", 30, true);
            turno = agenda.turno(agenda.paciente("Ana", "Prueba"), consulta, lunesA("10:00"), 30, "RESERVADO",
                    false);
            bloqueo = agenda.bloqueo(lunesA("12:00"), lunesA("13:00"), null);
        }

        @ParameterizedTest(name = "{0} {1}")
        @MethodSource("ar.sistemaodontologico.consultorio.web.MatrizDeEscriturasTest#intentos")
        void ningunaVarianteDeLaRutaLlegaAUnaEscritura(String metodo, String plantilla) {
            String ruta = plantilla.replace("{t}", turno.toString()).replace("{b}", bloqueo.toString());

            ResponseEntity<String> respuesta = http.exchange(RequestEntity
                    .method(HttpMethod.valueOf(metodo), URI.create("http://localhost:" + puerto + ruta))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("accion", "CONFIRMAR", "novedadAt", "2026-11-21T13:00:00Z", "version", 0)),
                    String.class);

            assertThat(respuesta.getStatusCode().value()).as(respuesta.toString()).isIn(400, 403, 404, 405);
            if (respuesta.getStatusCode().value() == 403) {
                assertThat(respuesta.getBody()).contains("PLAN_VENCIDO");
            }
            assertThat(jdbc.queryForObject("SELECT estado FROM turno WHERE id = ?", String.class, turno))
                    .isEqualTo("RESERVADO");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM bloqueo WHERE id = ?", Long.class, bloqueo)).isOne();
            assertThat(accionesDelConsultorio(agenda)).isEmpty();
        }

        @Test
        void elMetodoNoSePuedeSobrescribir() {
            HttpHeaders cabeceras = new HttpHeaders();
            cabeceras.setBearerAuth(token);
            cabeceras.set("X-HTTP-Method-Override", "DELETE");
            cabeceras.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            ResponseEntity<String> respuesta = http.exchange(RequestEntity
                    .post(URI.create("http://localhost:" + puerto + "/api/v1/bloqueos/" + bloqueo + "?_method=DELETE"))
                    .headers(cabeceras).body("_method=DELETE"), String.class);

            assertThat(respuesta.getStatusCode().is2xxSuccessful()).isFalse();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM bloqueo WHERE id = ?", Long.class, bloqueo)).isOne();
        }
    }

    static Stream<String[]> intentos() {
        return Stream.of(
                new String[] {"POST", "/api/v1/turnos/{t}/visto/"},
                new String[] {"PATCH", "/api/v1/turnos/{t}/estado/"},
                new String[] {"PATCH", "/api/v1/turnos/{t}/estado;/visto"},
                new String[] {"PATCH", "/api/v1/turnos/{t}/estado;x=visto"},
                new String[] {"PATCH", "/api/v1/turnos/{t};/estado"},
                new String[] {"PATCH", "/api/v1//turnos/{t}/estado"},
                new String[] {"PATCH", "//api/v1/turnos/{t}/estado"},
                new String[] {"POST", "/api/v1/Turnos/{t}/visto"},
                new String[] {"POST", "/api/v1/turnos/{t}/VISTO"},
                new String[] {"PATCH", "/API/V1/turnos/{t}/estado"},
                new String[] {"PATCH", "/api/v1/turnos/{t}%2Festado"},
                new String[] {"PATCH", "/api/v1/turnos/{t}/%65stado"},
                new String[] {"PATCH", "/api/v1/auth/%2e%2e/turnos/{t}/estado"},
                new String[] {"PATCH", "/api/v1/auth/../turnos/{t}/estado"},
                new String[] {"PATCH", "/api/v1/public/%2e%2e/turnos/{t}/estado"},
                new String[] {"PATCH", "/api/v1/turnos/{t}/visto/../estado"},
                new String[] {"PATCH", "/api/v1/turnos/{t}/./estado"},
                new String[] {"DELETE", "/api/v1/bloqueos/{b}/"},
                new String[] {"DELETE", "/api/v1/bloqueos/{b};"},
                new String[] {"DELETE", "/api/v1/auth/..;/bloqueos/{b}"},
                new String[] {"patch", "/api/v1/turnos/{t}/estado"});
    }

    private List<Escritura> escrituras() {
        List<Escritura> escrituras = new ArrayList<>();
        for (RequestMappingInfo info : mapeos.getHandlerMethods().keySet()) {
            for (RequestMethod metodo : info.getMethodsCondition().getMethods()) {
                if (!ESCRITURAS.contains(metodo)) {
                    continue;
                }
                info.getPatternValues().stream()
                        .filter(patron -> patron.startsWith("/api/v1/"))
                        .forEach(patron -> escrituras.add(new Escritura(metodo.name(), patron)));
            }
        }
        assertThat(escrituras).as("escrituras registradas").hasSizeGreaterThan(20);
        return escrituras;
    }
}
