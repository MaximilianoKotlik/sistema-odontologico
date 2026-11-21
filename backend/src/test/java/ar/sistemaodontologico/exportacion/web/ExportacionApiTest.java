package ar.sistemaodontologico.exportacion.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pruebas.agenda.AgendaDePrueba.lunesA;

import ar.sistemaodontologico.compartido.IntegracionPostgresTest;
import ar.sistemaodontologico.compartido.application.ConsultaDeAuditoria;
import ar.sistemaodontologico.compartido.application.PublicadorDeAuditoria;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import ar.sistemaodontologico.exportacion.application.CandadoDeExportacion;
import ar.sistemaodontologico.exportacion.application.ExportadorDeEntidad;
import ar.sistemaodontologico.exportacion.application.ExportarDatos;
import ar.sistemaodontologico.exportacion.application.GeneradorDeResumenPdf;
import ar.sistemaodontologico.exportacion.application.GuardiaDeExportaciones;
import ar.sistemaodontologico.exportacion.application.PedidoDeExportacion;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import pruebas.agenda.AgendaDePrueba;
import pruebas.seguridad.TokensDePrueba;

/** {@code POST /api/v1/exportacion} de punta a punta (C-19, tareas 5.2 a 6.3), con PostgreSQL real y datos ficticios. */
@AutoConfigureMockMvc
class ExportacionApiTest extends IntegracionPostgresTest {

    private static final String RUTA = "/api/v1/exportacion";
    private static final String IP = "203.0.113.10";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private GuardiaDeExportaciones guardia;
    @Autowired
    private List<ExportadorDeEntidad> exportadores;
    @Autowired
    private GeneradorDeResumenPdf resumen;
    @Autowired
    private CandadoDeExportacion candado;
    @Autowired
    private ConsultaDeAuditoria consulta;
    @Autowired
    private PublicadorDeAuditoria publicador;
    @Autowired
    private PlatformTransactionManager transacciones;
    @Autowired
    private Clock clock;

    private AgendaDePrueba agenda;
    private UUID prestacion;

    @BeforeEach
    void datos() {
        agenda = AgendaDePrueba.nueva(jdbc);
        prestacion = agenda.prestacion("Consulta", 30, true);
    }

    @Test
    void descargaElZipConSusCabecerasYLoAudita() throws Exception {
        UUID paciente = agenda.paciente("Uno", "Prueba");
        UUID turno = agenda.turno(paciente, prestacion, lunesA("10:00"), 30, "RESERVADO", false);

        MvcResult resultado = exportar(agenda);

        assertThat(resultado.getResponse().getContentType()).isEqualTo("application/zip");
        assertThat(resultado.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .matches("attachment; filename=\"export-cuenta-[0-9a-f]{8}-\\d{4}-\\d{2}-\\d{2}\\.zip\"");
        assertThat(resultado.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
        Map<String, byte[]> zip = abrir(resultado);
        assertThat(texto(zip, "turnos.csv")).contains(turno.toString(), paciente.toString());
        Map<String, Object> evento = jdbc.queryForMap("""
                SELECT actor_tipo, actor_id, host(ip) AS ip, valor_nuevo::text AS valor FROM evento_auditoria
                 WHERE consultorio_id = ? AND accion = 'EXPORTACION_GENERADA'""", agenda.consultorio());
        assertThat(evento.get("actor_tipo")).isEqualTo("ODONTOLOGO");
        assertThat(evento.get("actor_id")).isEqualTo(agenda.usuario());
        assertThat(evento.get("ip")).isEqualTo(IP);
        assertThat(evento.get("valor").toString()).contains("\"turnos\": 1").doesNotContain("Prueba");
        assertThat(guardia.estaTomada(new ConsultorioId(agenda.consultorio()))).isFalse();
    }

    @Test
    void sinSesionEs401() throws Exception {
        mvc.perform(post(RUTA)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void otraExportacionALosDosMinutosEs429() throws Exception {
        eventoDeExportacion(agenda, Duration.ofMinutes(2));

        MvcResult resultado = mvc.perform(pedido(agenda)).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.codigo").value("EXPORTACION_RECIENTE")).andReturn();

        assertThat(Long.parseLong(resultado.getResponse().getHeader(HttpHeaders.RETRY_AFTER))).isBetween(170L, 180L);
        assertThat(guardia.estaTomada(new ConsultorioId(agenda.consultorio()))).isFalse();
    }

    @Test
    void pasadosCincoMinutosSePuedeExportar() throws Exception {
        eventoDeExportacion(agenda, Duration.ofMinutes(6));

        exportar(agenda);
    }

    @Test
    void conOtraEnCursoEs429Con30Segundos() throws Exception {
        ConsultorioId consultorio = new ConsultorioId(agenda.consultorio());
        assertThat(guardia.tomar(consultorio)).isTrue();
        try {
            mvc.perform(pedido(agenda)).andExpect(status().isTooManyRequests())
                    .andExpect(header().string(HttpHeaders.RETRY_AFTER, "30"));
        } finally {
            guardia.liberar(consultorio);
        }
    }

    @Test
    void otroConsultorioNoCompartelElLimite() throws Exception {
        eventoDeExportacion(agenda, Duration.ofMinutes(1));

        exportar(AgendaDePrueba.nueva(jdbc));
    }

    @Test
    void funcionaConElPlanVencido() throws Exception {
        jdbc.update("UPDATE consultorio SET plan = 'VENCIDO' WHERE id = ?", agenda.consultorio());

        assertThat(abrir(exportar(agenda))).containsKey("manifest.json");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM evento_auditoria
                 WHERE consultorio_id = ? AND accion = 'EXPORTACION_GENERADA'""", Long.class, agenda.consultorio()))
                .isEqualTo(1);
    }

    @Test
    void nombreConFormulaSeEscapaEnCsvYSeConservaEnJson() throws Exception {
        agenda.paciente("=HIPERVINCULO(\"x\")", "Prueba");

        Map<String, byte[]> zip = abrir(exportar(agenda));

        assertThat(texto(zip, "pacientes.csv")).contains("\"'=HIPERVINCULO(\"\"x\"\")\"");
        assertThat(json.readTree(zip.get("pacientes.json")).get(0).get("nombre").asText())
                .isEqualTo("=HIPERVINCULO(\"x\")");
    }

    @Test
    void elPdfConservaLosNombresYCoincideConElManifest() throws Exception {
        UUID paciente = agenda.paciente("Pacienta", "Ñandú Pérez");
        agenda.turno(paciente, prestacion, lunesA("10:00"), 30, "RESERVADO", false);

        Map<String, byte[]> zip = abrir(exportar(agenda));

        JsonNode manifest = json.readTree(zip.get("manifest.json"));
        assertThat(manifest.get("entidades").get("turnos").asLong()).isEqualTo(1);
        assertThat(textoDelPdf(zip)).contains("Ñandú Pérez", "Turnos (1)");
    }

    @Test
    void ningunArchivoContieneSecretos() throws Exception {
        UUID paciente = agenda.paciente("Uno", "Prueba");
        UUID turno = agenda.turno(paciente, prestacion, lunesA("10:00"), 30, "RESERVADO", false);
        String hashContrasena = "$2a$12$hashficticiodeprueba";
        String refresh = "a".repeat(32) + UUID.randomUUID().toString().replace("-", "");
        String recuperacion = "b".repeat(32) + UUID.randomUUID().toString().replace("-", "");
        String gestion = "c".repeat(32) + UUID.randomUUID().toString().replace("-", "");
        Timestamp vence = Timestamp.from(Instant.now().plusSeconds(3600));
        jdbc.update("""
                INSERT INTO refresh_token (id, consultorio_id, usuario_id, token_hash, expira_at)
                VALUES (?, ?, ?, ?, ?)""", UUID.randomUUID(), agenda.consultorio(), agenda.usuario(), refresh, vence);
        jdbc.update("""
                INSERT INTO token_recuperacion (id, consultorio_id, usuario_id, token_hash, expira_at)
                VALUES (?, ?, ?, ?, ?)""", UUID.randomUUID(), agenda.consultorio(), agenda.usuario(), recuperacion, vence);
        jdbc.update("INSERT INTO token_gestion_turno (id, consultorio_id, turno_id, token_hash) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), agenda.consultorio(), turno, gestion);

        Map<String, byte[]> zip = abrir(exportar(agenda));

        List<String> contenidos = new ArrayList<>();
        zip.forEach((nombre, bytes) -> {
            if (!nombre.endsWith(".pdf")) {
                contenidos.add(new String(bytes, StandardCharsets.UTF_8));
            }
        });
        contenidos.add(textoDelPdf(zip));
        assertThat(contenidos).allSatisfy(contenido -> assertThat(contenido)
                .doesNotContain(hashContrasena, refresh, recuperacion, gestion, "password_hash", "token_hash"));
    }

    @Test
    void soloExportaLosDatosDelConsultorioDeLaSesion() throws Exception {
        AgendaDePrueba otra = AgendaDePrueba.nueva(jdbc);
        UUID prestacionAjena = otra.prestacion("Ajena", 30, true);
        UUID pacienteAjeno = otra.paciente("NombreAjeno", "ApellidoAjeno");
        UUID turnoAjeno = otra.turno(pacienteAjeno, prestacionAjena, lunesA("10:00"), 30, "RESERVADO", false);
        UUID paciente = agenda.paciente("Uno", "Prueba");
        agenda.turno(paciente, prestacion, lunesA("10:00"), 30, "RESERVADO", false);

        Map<String, byte[]> zip = abrir(exportar(agenda));

        List<String> contenidos = new ArrayList<>();
        zip.forEach((nombre, bytes) -> {
            if (!nombre.endsWith(".pdf")) {
                contenidos.add(new String(bytes, StandardCharsets.UTF_8));
            }
        });
        contenidos.add(textoDelPdf(zip));
        assertThat(contenidos).allSatisfy(contenido -> assertThat(contenido).doesNotContain(
                otra.consultorio().toString(), pacienteAjeno.toString(), turnoAjeno.toString(), "NombreAjeno"));
    }

    @Test
    void unTurnoCreadoDuranteLaExportacionQuedaIgualEnManifestJsonYCsv() throws Exception {
        UUID paciente = agenda.paciente("Uno", "Prueba");
        agenda.turno(paciente, prestacion, lunesA("10:00"), 30, "RESERVADO", false);
        AtomicBoolean insertado = new AtomicBoolean();
        List<ExportadorDeEntidad> conEscritura = exportadores.stream()
                .map(e -> e.entidad().equals("turnos") ? insertaDespuesDeLeer(e, paciente, insertado) : e).toList();
        ExportarDatos caso = new ExportarDatos(conEscritura, resumen, guardia, candado, consulta, publicador,
                transacciones, json, clock);
        PedidoDeExportacion pedido =
                new PedidoDeExportacion(new ConsultorioId(agenda.consultorio()), agenda.usuario(), IP, clock.instant());
        assertThat(caso.reservar(pedido).permitida()).isTrue();

        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        caso.generar(pedido, salida);

        assertThat(insertado).isTrue();
        Map<String, byte[]> zip = abrir(salida.toByteArray());
        long enManifest = json.readTree(zip.get("manifest.json")).get("entidades").get("turnos").asLong();
        assertThat(enManifest).isEqualTo(1);
        assertThat(json.readTree(zip.get("turnos.json"))).hasSize(1);
        assertThat(texto(zip, "turnos.csv").split("\r\n")).hasSize(2);
    }

    /** Después de la primera lectura confirma un turno desde otro hilo, con su propia conexión. */
    private ExportadorDeEntidad insertaDespuesDeLeer(ExportadorDeEntidad real, UUID paciente, AtomicBoolean insertado) {
        return new ExportadorDeEntidad() {
            @Override
            public String entidad() {
                return real.entidad();
            }

            @Override
            public List<String> columnas() {
                return real.columnas();
            }

            @Override
            public long exportar(ConsultorioId consultorio, ConsumidorDeFila consumidor) throws IOException {
                long cantidad = real.exportar(consultorio, consumidor);
                if (insertado.compareAndSet(false, true)) {
                    CompletableFuture.runAsync(
                            () -> agenda.turno(paciente, prestacion, lunesA("11:00"), 30, "RESERVADO", false)).join();
                }
                return cantidad;
            }
        };
    }

    private MvcResult exportar(AgendaDePrueba de) throws Exception {
        MvcResult iniciado = mvc.perform(pedido(de)).andExpect(request().asyncStarted()).andReturn();
        return mvc.perform(asyncDispatch(iniciado)).andExpect(status().isOk()).andReturn();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder pedido(AgendaDePrueba de) {
        String token = TokensDePrueba.valido(SECRETO_JWT_DE_PRUEBA, de.usuario(), de.consultorio(), 1);
        return post(RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).with(peticion -> {
            peticion.setRemoteAddr(IP);
            return peticion;
        });
    }

    private void eventoDeExportacion(AgendaDePrueba de, Duration hace) {
        jdbc.update("""
                INSERT INTO evento_auditoria (id, consultorio_id, ocurrido_at, actor_tipo, actor_id, entidad, entidad_id, accion)
                VALUES (?, ?, ?, 'ODONTOLOGO', ?, 'consultorio', ?, 'EXPORTACION_GENERADA')""", UUID.randomUUID(),
                de.consultorio(), Timestamp.from(Instant.now().minus(hace)), de.usuario(), de.consultorio());
    }

    private static Map<String, byte[]> abrir(MvcResult resultado) throws IOException {
        return abrir(resultado.getResponse().getContentAsByteArray());
    }

    private static Map<String, byte[]> abrir(byte[] contenido) throws IOException {
        Map<String, byte[]> entradas = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(contenido))) {
            for (ZipEntry entrada = zip.getNextEntry(); entrada != null; entrada = zip.getNextEntry()) {
                entradas.put(entrada.getName(), zip.readAllBytes());
            }
        }
        return entradas;
    }

    private static String texto(Map<String, byte[]> zip, String nombre) {
        return new String(zip.get(nombre), StandardCharsets.UTF_8);
    }

    private static String textoDelPdf(Map<String, byte[]> zip) throws IOException {
        try (PDDocument documento = Loader.loadPDF(zip.get("resumen.pdf"))) {
            return new PDFTextStripper().getText(documento);
        }
    }
}
