package ar.sistemaodontologico.compartido.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static pruebas.agenda.AgendaDePrueba.lunesA;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import ar.sistemaodontologico.consultorio.web.ApiDelPlanTest;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import pruebas.agenda.AgendaDePrueba;

/**
 * Matriz de aislamiento entre consultorios (C-23, tareas 3.1 y 3.2, design D6; spec aislamiento-tenant). Recorre todos
 * los endpoints privados que registra Spring MVC, como {@code MatrizDeEscriturasTest}:
 *
 * <ul>
 *   <li>Un endpoint privado que no está en el {@link #REGISTRO} hace fallar el test con su método y su ruta.</li>
 *   <li>Con un id del otro consultorio (en la ruta o en el cuerpo): 404 {@code RECURSO_NO_ENCONTRADO}, sin cambios en
 *       los datos ni en la auditoría de ninguno de los dos.</li>
 *   <li>Sin ids ajenos (listados y altas): la respuesta no trae datos del otro consultorio y sus datos no cambian.</li>
 * </ul>
 *
 * El consultorio de la sesión es {@code agenda} (A) y el ajeno es {@code otro} (B). Los cuerpos son válidos con los
 * datos de B, así una falla de aislamiento llega a escribir y el test la detecta. Datos ficticios (regla dura 17).
 */
class MatrizDeAislamientoTest extends ApiDelPlanTest {

    /** Los datos de B que usan las rutas y los cuerpos del registro. */
    record Ajeno(UUID turno, UUID bloqueo, UUID paciente, UUID prestacion, String dni, Instant informadoAt,
            Instant novedadAt) {
    }

    /**
     * Cómo se prueba un endpoint. {@code conIdAjeno}: la ruta o el cuerpo llevan un id de B y se espera 404. Si no, es
     * un listado o un alta del consultorio de la sesión y se verifica que la respuesta no traiga datos de B.
     */
    record Caso(boolean conIdAjeno, Function<Ajeno, String> ruta, Function<Ajeno, String> cuerpo) {

        static Caso idAjeno(Function<Ajeno, String> ruta) {
            return new Caso(true, ruta, b -> null);
        }

        static Caso idAjeno(Function<Ajeno, String> ruta, Function<Ajeno, String> cuerpo) {
            return new Caso(true, ruta, cuerpo);
        }

        static Caso sinIdAjeno(String ruta) {
            return new Caso(false, b -> ruta, b -> null);
        }

        static Caso listado(Function<Ajeno, String> ruta) {
            return new Caso(false, ruta, b -> null);
        }

        static Caso sinIdAjeno(String ruta, String cuerpo) {
            return new Caso(false, b -> ruta, b -> cuerpo);
        }
    }

    private static final String SEMANA = "?desde=" + AgendaDePrueba.LUNES + "&hasta=" + AgendaDePrueba.LUNES.plusSeconds(7 * 86_400L);

    /**
     * Registro de los endpoints privados (tarea 3.2). Un endpoint nuevo se suma acá con su recurso y un cuerpo válido;
     * si no, {@link #todoEndpointPrivadoEstaEnElRegistro()} falla y lo nombra.
     */
    private static final Map<String, Caso> REGISTRO = registro();

    private static Map<String, Caso> registro() {
        Map<String, Caso> r = new LinkedHashMap<>();
        // Agenda y turnos (C-10, C-11, C-13, C-14)
        r.put("GET /api/v1/agenda", Caso.sinIdAjeno("/api/v1/agenda" + SEMANA));
        r.put("GET /api/v1/turnos/fuera-de-disponibilidad", Caso.sinIdAjeno("/api/v1/turnos/fuera-de-disponibilidad"));
        r.put("GET /api/v1/turnos/{id}", Caso.idAjeno(b -> "/api/v1/turnos/" + b.turno()));
        // Alta de un turno con el paciente y la prestación de B en el cuerpo.
        r.put("POST /api/v1/turnos", Caso.idAjeno(b -> "/api/v1/turnos", b -> """
                {"pacienteId":"%s","prestacionId":"%s","inicio":"%s","esSobreturno":false}"""
                .formatted(b.paciente(), b.prestacion(), lunesA("11:00"))));
        r.put("PATCH /api/v1/turnos/{id}/estado", Caso.idAjeno(b -> "/api/v1/turnos/" + b.turno() + "/estado",
                b -> "{\"accion\":\"CANCELAR\",\"version\":0}"));
        r.put("POST /api/v1/turnos/{id}/reprogramar", Caso.idAjeno(b -> "/api/v1/turnos/" + b.turno() + "/reprogramar",
                b -> "{\"inicio\":\"%s\",\"version\":0}".formatted(lunesA("11:00"))));
        r.put("PATCH /api/v1/turnos/{id}/duracion", Caso.idAjeno(b -> "/api/v1/turnos/" + b.turno() + "/duracion",
                b -> "{\"duracionMin\":45,\"version\":0}"));
        r.put("PATCH /api/v1/turnos/{id}/nota", Caso.idAjeno(b -> "/api/v1/turnos/" + b.turno() + "/nota",
                b -> "{\"nota\":\"Nota ajena\",\"version\":0}"));
        r.put("POST /api/v1/turnos/{id}/visto", Caso.idAjeno(b -> "/api/v1/turnos/" + b.turno() + "/visto",
                b -> "{\"novedadAt\":\"%s\"}".formatted(b.novedadAt())));
        // Bloqueos (C-11)
        r.put("GET /api/v1/bloqueos", Caso.sinIdAjeno("/api/v1/bloqueos"));
        r.put("POST /api/v1/bloqueos", Caso.sinIdAjeno("/api/v1/bloqueos",
                "{\"inicio\":\"%s\",\"fin\":\"%s\",\"motivo\":\"Curso\"}".formatted(lunesA("15:00"), lunesA("16:00"))));
        r.put("DELETE /api/v1/bloqueos/{id}", Caso.idAjeno(b -> "/api/v1/bloqueos/" + b.bloqueo()));
        // Pacientes (C-12, C-17)
        // Búsqueda con el DNI de un paciente de B: la respuesta no lo trae.
        r.put("GET /api/v1/pacientes", Caso.listado(b -> "/api/v1/pacientes?q=" + b.dni()));
        r.put("GET /api/v1/pacientes/listado", Caso.sinIdAjeno("/api/v1/pacientes/listado"));
        r.put("GET /api/v1/pacientes/{id}", Caso.idAjeno(b -> "/api/v1/pacientes/" + b.paciente()));
        r.put("POST /api/v1/pacientes/{id}/confirmar-revision",
                Caso.idAjeno(b -> "/api/v1/pacientes/" + b.paciente() + "/confirmar-revision",
                        b -> "{\"informadoAt\":\"%s\"}".formatted(b.informadoAt())));
        r.put("PATCH /api/v1/pacientes/{id}", Caso.idAjeno(b -> "/api/v1/pacientes/" + b.paciente(),
                b -> "{\"informadoAt\":\"%s\",\"nombre\":\"Otro\",\"apellido\":\"Nombre\",\"email\":null}"
                        .formatted(b.informadoAt())));
        // Auditoría (C-18)
        r.put("GET /api/v1/turnos/{id}/historial", Caso.idAjeno(b -> "/api/v1/turnos/" + b.turno() + "/historial"));
        r.put("GET /api/v1/auditoria", Caso.sinIdAjeno("/api/v1/auditoria"));
        // Configuración del consultorio (C-08)
        r.put("GET /api/v1/consultorio", Caso.sinIdAjeno("/api/v1/consultorio"));
        r.put("PUT /api/v1/consultorio", Caso.sinIdAjeno("/api/v1/consultorio", "{}"));
        r.put("GET /api/v1/disponibilidad", Caso.sinIdAjeno("/api/v1/disponibilidad"));
        r.put("PUT /api/v1/disponibilidad", Caso.sinIdAjeno("/api/v1/disponibilidad", "{}"));
        r.put("GET /api/v1/prestaciones", Caso.sinIdAjeno("/api/v1/prestaciones?incluirInactivas=true"));
        r.put("POST /api/v1/prestaciones",
                Caso.sinIdAjeno("/api/v1/prestaciones", "{\"nombre\":\"Ortodoncia\",\"duracionMin\":30}"));
        r.put("GET /api/v1/prestaciones/{id}", Caso.idAjeno(b -> "/api/v1/prestaciones/" + b.prestacion()));
        r.put("PUT /api/v1/prestaciones/{id}", Caso.idAjeno(b -> "/api/v1/prestaciones/" + b.prestacion(),
                b -> "{\"nombre\":\"Ajena\",\"duracionMin\":30}"));
        r.put("POST /api/v1/prestaciones/{id}/desactivar",
                Caso.idAjeno(b -> "/api/v1/prestaciones/" + b.prestacion() + "/desactivar"));
        r.put("POST /api/v1/prestaciones/{id}/activar",
                Caso.idAjeno(b -> "/api/v1/prestaciones/" + b.prestacion() + "/activar"));
        // Plan (C-20) y exportación (C-19)
        r.put("GET /api/v1/plan", Caso.sinIdAjeno("/api/v1/plan"));
        r.put("POST /api/v1/exportacion", Caso.sinIdAjeno("/api/v1/exportacion"));
        return Map.copyOf(r);
    }

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mapeos;

    private AgendaDePrueba otro;
    private Ajeno ajeno;
    private List<String> marcasDeB;

    /** A y B con datos propios: prestación, paciente marcado para revisión, turno con novedad, bloqueo y evento. */
    @BeforeEach
    void dosConsultoriosConDatos() {
        poblar(agenda, "Ana", "Propia");
        otro = AgendaDePrueba.nueva(jdbc);
        ajeno = poblar(otro, "Bruno", "Ajeno");
        marcasDeB = new ArrayList<>(List.of(otro.consultorio().toString(), ajeno.turno().toString(),
                ajeno.bloqueo().toString(), ajeno.paciente().toString(), ajeno.prestacion().toString(), ajeno.dni(),
                "Bruno", "Ajeno"));
    }

    private Ajeno poblar(AgendaDePrueba de, String nombre, String apellido) {
        UUID prestacion = de.prestacion("Consulta " + apellido, 30, true);
        String dni = AgendaDePrueba.dniNuevo();
        UUID paciente = de.paciente(nombre, apellido, dni, "+5491100000077", null);
        UUID turno = de.turno(paciente, prestacion, lunesA("10:00"), 30, "RESERVADO", false);
        Instant informadoAt = lunesA("08:00");
        de.informado(paciente, nombre + "a", apellido, null, informadoAt, turno);
        Instant novedadAt = lunesA("08:30");
        de.novedad(turno, novedadAt);
        de.eventoDelPaciente(turno, "TURNO_CREADO", "null", "{\"estado\":\"RESERVADO\"}", lunesA("08:00"));
        UUID bloqueo = de.bloqueo(lunesA("12:00"), lunesA("13:00"), "Curso " + apellido);
        return new Ajeno(turno, bloqueo, paciente, prestacion, dni, informadoAt, novedadAt);
    }

    // Scenario: Endpoint nuevo sin cubrir
    @Test
    void todoEndpointPrivadoEstaEnElRegistro() {
        List<String> sinCubrir = endpointsPrivados().stream().filter(e -> !REGISTRO.containsKey(e)).toList();

        assertThat(sinCubrir).as("Endpoints privados que no están en el registro de la matriz de aislamiento")
                .isEmpty();
        assertThat(REGISTRO.keySet()).as("Endpoints del registro que ya no existen").isSubsetOf(endpointsPrivados());
    }

    // Scenario: Matriz de endpoints privados / Endpoint nuevo sin aislar / Endpoint sin id en la ruta
    @Test
    void ningunEndpointPrivadoLlegaADatosDeOtroConsultorio() throws Exception {
        SoftAssertions softly = new SoftAssertions();
        for (Map.Entry<String, Caso> entrada : REGISTRO.entrySet()) {
            String endpoint = entrada.getKey();
            Caso caso = entrada.getValue();
            String antesA = estado(agenda.consultorio());
            String antesB = estado(otro.consultorio());

            MockHttpServletResponse respuesta = enviar(endpoint, caso);
            // El error repite la ruta pedida en "instance": ese id lo mandó el test, no es un dato devuelto.
            String cuerpo = texto(respuesta).replaceAll("\"instance\":\"[^\"]*\"", "");

            if (caso.conIdAjeno()) {
                softly.assertThat(respuesta.getStatus()).as(endpoint + " con un id de otro consultorio").isEqualTo(404);
                softly.assertThat(cuerpo).as(endpoint).contains("\"codigo\":\"RECURSO_NO_ENCONTRADO\"");
                softly.assertThat(estado(agenda.consultorio())).as(endpoint + " cambió datos de la sesión")
                        .isEqualTo(antesA);
            } else {
                softly.assertThat(respuesta.getStatus()).as(endpoint + " no responde un error del servidor")
                        .isLessThan(500);
            }
            for (String marca : marcasDeB) {
                softly.assertThat(cuerpo).as(endpoint + " devolvió datos de otro consultorio").doesNotContain(marca);
            }
            softly.assertThat(estado(otro.consultorio())).as(endpoint + " cambió datos de otro consultorio")
                    .isEqualTo(antesB);
        }
        softly.assertAll();
    }

    private MockHttpServletResponse enviar(String endpoint, Caso caso) throws Exception {
        String metodo = endpoint.substring(0, endpoint.indexOf(' '));
        var pedido = request(HttpMethod.valueOf(metodo), caso.ruta().apply(ajeno))
                .header(HttpHeaders.ORIGIN, ORIGEN_FRONTEND);
        String cuerpo = caso.cuerpo().apply(ajeno);
        if (cuerpo != null) {
            pedido.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        }
        MvcResult resultado = pedir(pedido).andReturn();
        // La exportación responde de forma asíncrona: hay que esperar el ZIP y su evento antes de comparar.
        if (resultado.getRequest().isAsyncStarted()) {
            resultado = mvc.perform(asyncDispatch(resultado)).andReturn();
        }
        return resultado.getResponse();
    }

    /** El cuerpo de la respuesta como texto; la exportación es un ZIP, así que se leen todas sus entradas. */
    private static String texto(MockHttpServletResponse respuesta) throws IOException {
        byte[] bytes = respuesta.getContentAsByteArray();
        if (!"application/zip".equals(respuesta.getContentType())) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        StringBuilder contenido = new StringBuilder();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (ZipEntry entrada = zip.getNextEntry(); entrada != null; entrada = zip.getNextEntry()) {
                contenido.append(new String(zip.readAllBytes(), StandardCharsets.UTF_8)).append('\n');
            }
        }
        return contenido.toString();
    }

    /**
     * Una huella de todas las filas del consultorio: el consultorio y cada tabla con {@code consultorio_id}, incluida
     * la auditoría. Una tabla nueva entra sola.
     */
    private String estado(UUID consultorio) {
        List<String> tablas = jdbc.queryForList("""
                SELECT table_name FROM information_schema.columns
                 WHERE table_schema = 'public' AND column_name = 'consultorio_id'
                 ORDER BY table_name""", String.class);
        StringBuilder huella = new StringBuilder(jdbc.queryForObject(
                "SELECT md5(c::text) FROM consultorio c WHERE id = ?", String.class, consultorio));
        for (String tabla : tablas) {
            huella.append('|').append(tabla).append('=').append(jdbc.queryForObject(
                    "SELECT coalesce(md5(string_agg(t::text, ',' ORDER BY t::text)), '-') FROM " + tabla
                            + " t WHERE consultorio_id = ?", String.class, consultorio));
        }
        return huella.toString();
    }

    /** Los endpoints bajo {@code /api/v1/}, fuera de {@code /api/v1/public/**} y {@code /api/v1/auth/**}. */
    private List<String> endpointsPrivados() {
        TreeSet<String> endpoints = new TreeSet<>();
        for (RequestMappingInfo info : mapeos.getHandlerMethods().keySet()) {
            for (String patron : info.getPatternValues()) {
                if (!patron.startsWith("/api/v1/") || patron.startsWith("/api/v1/public/")
                        || patron.startsWith("/api/v1/auth/")) {
                    continue;
                }
                for (RequestMethod metodo : info.getMethodsCondition().getMethods()) {
                    endpoints.add(metodo.name() + " " + patron);
                }
            }
        }
        return new ArrayList<>(endpoints);
    }
}
