package ar.sistemaodontologico.exportacion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.sistemaodontologico.compartido.application.ConsultaDeAuditoria;
import ar.sistemaodontologico.compartido.application.PublicadorDeAuditoria;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import ar.sistemaodontologico.compartido.domain.auditoria.ActorTipo;
import ar.sistemaodontologico.compartido.domain.auditoria.CambioAuditado;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

/** Caso de uso de la exportación (C-19, tareas 5.1, 5.3, 5.3b y 5.4; design D2, D7 y D8). */
class ExportarDatosTest {

    private static final Instant AHORA = Instant.parse("2030-03-05T02:30:00Z");
    private static final UUID USUARIO = UUID.fromString("00000000-0000-7000-8000-000000000009");

    private final ConsultorioId consultorio = new ConsultorioId(UUID.randomUUID());
    private final PedidoDeExportacion pedido =
            new PedidoDeExportacion(consultorio, USUARIO, "203.0.113.10", AHORA);
    private final ObjectMapper mapper = new ObjectMapper();
    private final GuardiaDeExportaciones guardia =
            new GuardiaDeExportaciones(Clock.fixed(AHORA, ZoneOffset.UTC), Duration.ofMinutes(5));
    private final ConsultaFalsa consulta = new ConsultaFalsa();
    private final List<CambioAuditado> eventos = new ArrayList<>();
    private final TransaccionesFalsas transacciones = new TransaccionesFalsas();
    private PublicadorDeAuditoria publicador = eventos::add;
    private boolean candadoLibre = true;

    @Test
    void reservaYNombraElArchivoConLaFechaLocal() {
        ExportarDatos.Reserva reserva = caso(exportadores()).reservar(pedido);

        assertThat(reserva.permitida()).isTrue();
        // 02:30 UTC del 5 son las 23:30 del 4 en Buenos Aires.
        assertThat(reserva.nombreArchivo()).isEqualTo("export-consultorio-prueba-2030-03-04.zip");
    }

    @Test
    void otroPedidoEnCursoEsRechazadoCon30Segundos() {
        ExportarDatos caso = caso(exportadores());
        caso.reservar(pedido);

        ExportarDatos.Reserva segunda = caso.reservar(pedido);

        assertThat(segunda.permitida()).isFalse();
        assertThat(segunda.reintentarEnSegundos()).isEqualTo(30);
    }

    @Test
    void exportacionDeHaceDosMinutosEsRechazadaYLiberaLaGuardia() {
        consulta.ultima = AHORA.minus(Duration.ofMinutes(2));

        ExportarDatos.Reserva reserva = caso(exportadores()).reservar(pedido);

        assertThat(reserva.permitida()).isFalse();
        assertThat(reserva.reintentarEnSegundos()).isEqualTo(180);
        assertThat(guardia.estaTomada(consultorio)).isFalse();
    }

    @Test
    void redondeaHaciaArribaLosSegundosRestantes() {
        consulta.ultima = AHORA.minus(Duration.ofMinutes(2)).plusMillis(1);

        assertThat(caso(exportadores()).reservar(pedido).reintentarEnSegundos()).isEqualTo(181);
    }

    @Test
    void pasadosCincoMinutosSePermite() {
        consulta.ultima = AHORA.minus(Duration.ofMinutes(6));

        assertThat(caso(exportadores()).reservar(pedido).permitida()).isTrue();
    }

    @Test
    void justoALosCincoMinutosSePermite() {
        consulta.ultima = AHORA.minus(Duration.ofMinutes(5));

        assertThat(caso(exportadores()).reservar(pedido).permitida()).isTrue();
    }

    @Test
    void otroConsultorioNoCompartelaGuardia() {
        ExportarDatos caso = caso(exportadores());
        caso.reservar(pedido);
        PedidoDeExportacion otro =
                new PedidoDeExportacion(new ConsultorioId(UUID.randomUUID()), USUARIO, null, AHORA);

        assertThat(caso.reservar(otro).permitida()).isTrue();
    }

    @Test
    void unErrorAlReservarLiberaLaGuardia() {
        consulta.falla = true;

        assertThatThrownBy(() -> caso(exportadores()).reservar(pedido)).isInstanceOf(IllegalStateException.class);

        assertThat(guardia.estaTomada(consultorio)).isFalse();
    }

    @Test
    void generaElZipCompletoConElManifestAlFinal() throws IOException {
        ExportarDatos caso = caso(exportadores());
        caso.reservar(pedido);

        Map<String, byte[]> zip = abrir(generar(caso));

        assertThat(zip.keySet()).contains("LEEME.txt", "resumen.pdf");
        for (String entidad : List.of("consultorio", "odontologo", "prestaciones", "disponibilidad", "bloqueos",
                "pacientes", "turnos", "auditoria")) {
            assertThat(zip.keySet()).contains(entidad + ".json", entidad + ".csv");
        }
        assertThat(new ArrayList<>(zip.keySet()).getLast()).isEqualTo("manifest.json");
    }

    @Test
    void elManifestCoincideConLosArchivos() throws Exception {
        ExportarDatos caso = caso(exportadores());
        caso.reservar(pedido);

        Map<String, byte[]> zip = abrir(generar(caso));
        JsonNode manifest = mapper.readTree(zip.get("manifest.json"));

        assertThat(manifest.get("entidades").get("turnos").asLong()).isEqualTo(1);
        assertThat(manifest.get("entidades").get("bloqueos").asLong()).isZero();
        assertThat(manifest.get("archivos")).hasSize(zip.size() - 1);
        for (JsonNode archivo : manifest.get("archivos")) {
            byte[] contenido = zip.get(archivo.get("nombre").asText());
            assertThat(archivo.get("bytes").asLong()).isEqualTo(contenido.length);
            assertThat(archivo.get("sha256").asText()).isEqualTo(
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenido)));
        }
    }

    @Test
    void entidadVaciaQuedaConArregloYEncabezado() throws IOException {
        ExportarDatos caso = caso(exportadores());
        caso.reservar(pedido);

        Map<String, byte[]> zip = abrir(generar(caso));

        assertThat(new String(zip.get("bloqueos.json"))).isEqualTo("[]");
        assertThat(new String(zip.get("bloqueos.csv"))).isEqualTo("id,inicio\r\n");
    }

    @Test
    void registraElEventoConPedidoYCantidadesYLiberaLaGuardia() throws IOException {
        ExportarDatos caso = caso(exportadores());
        caso.reservar(pedido);

        generar(caso);

        assertThat(eventos).hasSize(1);
        CambioAuditado evento = eventos.getFirst();
        assertThat(evento.accion()).isEqualTo("EXPORTACION_GENERADA");
        assertThat(evento.consultorio()).isEqualTo(consultorio);
        assertThat(evento.entidad()).isEqualTo("consultorio");
        assertThat(evento.entidadId()).isEqualTo(consultorio.valor());
        assertThat(evento.actor().tipo()).isEqualTo(ActorTipo.ODONTOLOGO);
        assertThat(evento.actor().id()).isEqualTo(USUARIO);
        assertThat(evento.ip()).isEqualTo("203.0.113.10");
        assertThat(evento.valorNuevo()).containsEntry("versionEsquema", "1");
        assertThat(evento.valorNuevo().toString()).doesNotContain("Pacienta");
        assertThat(guardia.estaTomada(consultorio)).isFalse();
    }

    @Test
    void leeEnUnaFotoDeSoloLecturaYEscribeElEventoEnOtraTransaccion() throws IOException {
        ExportarDatos caso = caso(exportadores());
        caso.reservar(pedido);

        generar(caso);

        TransactionDefinition lectura = transacciones.definiciones.getFirst();
        assertThat(lectura.isReadOnly()).isTrue();
        assertThat(lectura.getIsolationLevel()).isEqualTo(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        assertThat(transacciones.definiciones.get(1).getPropagationBehavior())
                .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Test
    void siFallaUnExportadorNoHayZipValidoNiEventoYSeLiberaLaGuardia() {
        List<ExportadorDeEntidad> conFalla = new ArrayList<>(exportadores());
        conFalla.replaceAll(e -> e.entidad().equals("turnos") ? new ExportadorFalso("turnos", List.of("id"), null) : e);
        ExportarDatos caso = caso(conFalla);
        caso.reservar(pedido);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();

        assertThatThrownBy(() -> caso.generar(pedido, salida)).isInstanceOf(IOException.class);

        assertThat(eventos).isEmpty();
        assertThat(guardia.estaTomada(consultorio)).isFalse();
        assertThat(tieneDirectorioCentral(salida.toByteArray())).isFalse();
    }

    @Test
    void siElClienteCortaNoHayEventoYSeLiberaLaGuardia() {
        ExportarDatos caso = caso(exportadores());
        caso.reservar(pedido);
        OutputStream cortada = new OutputStream() {
            @Override
            public void write(int valor) throws IOException {
                throw new IOException("Conexión cortada");
            }
        };

        assertThatThrownBy(() -> caso.generar(pedido, cortada)).isInstanceOf(IOException.class);

        assertThat(eventos).isEmpty();
        assertThat(guardia.estaTomada(consultorio)).isFalse();
    }

    @Test
    void sinElCandadoSeCortaSinEventoYSeLiberaLaGuardia() {
        candadoLibre = false;
        ExportarDatos caso = caso(exportadores());
        caso.reservar(pedido);

        assertThatThrownBy(() -> caso.generar(pedido, new ByteArrayOutputStream()))
                .isInstanceOf(IllegalStateException.class);

        assertThat(eventos).isEmpty();
        assertThat(guardia.estaTomada(consultorio)).isFalse();
    }

    @Test
    void siFallaElEventoElZipYaEntregadoNoFallaYSeLiberaLaGuardia() throws IOException {
        publicador = cambio -> {
            throw new IllegalStateException("Sin base");
        };
        ExportarDatos caso = caso(exportadores());
        caso.reservar(pedido);

        byte[] zip = generar(caso);

        assertThat(abrir(zip)).containsKey("manifest.json");
        assertThat(guardia.estaTomada(consultorio)).isFalse();
    }

    @Test
    void liberarDevuelveLaGuardiaSiLaPeticionFallaAntesDelStreaming() {
        ExportarDatos caso = caso(exportadores());
        caso.reservar(pedido);

        caso.liberar(pedido);

        assertThat(caso.reservar(pedido).permitida()).isTrue();
    }

    private ExportarDatos caso(List<ExportadorDeEntidad> exportadores) {
        return new ExportarDatos(exportadores, new GeneradorDeResumenPdf(), guardia, c -> candadoLibre, consulta,
                cambio -> publicador.publicar(cambio), transacciones, mapper, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private byte[] generar(ExportarDatos caso) throws IOException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        caso.generar(pedido, salida);
        return salida.toByteArray();
    }

    private static Map<String, byte[]> abrir(byte[] zip) throws IOException {
        Map<String, byte[]> entradas = new LinkedHashMap<>();
        try (ZipInputStream entrada = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry actual = entrada.getNextEntry(); actual != null; actual = entrada.getNextEntry()) {
                entradas.put(actual.getName(), entrada.readAllBytes());
            }
        }
        return entradas;
    }

    /** Un ZIP terminado tiene la firma del fin del directorio central. */
    private static boolean tieneDirectorioCentral(byte[] zip) {
        byte[] firma = {0x50, 0x4b, 0x05, 0x06};
        for (int i = 0; i + firma.length <= zip.length; i++) {
            if (Arrays.equals(zip, i, i + firma.length, firma, 0, firma.length)) {
                return true;
            }
        }
        return false;
    }

    private static List<ExportadorDeEntidad> exportadores() {
        UUID paciente = UUID.randomUUID();
        UUID prestacion = UUID.randomUUID();
        Instant inicio = Instant.parse("2030-03-04T13:00:00Z");
        return List.of(
                new ExportadorFalso("consultorio",
                        List.of("nombre", "slug", "direccion", "telefono_contacto", "zona_horaria"),
                        List.of(Arrays.asList("Consultorio de prueba", "consultorio-prueba", null, null,
                                "America/Argentina/Buenos_Aires"))),
                new ExportadorFalso("odontologo", List.of("email"), List.of(List.of("odontologo@example.com"))),
                new ExportadorFalso("prestaciones", List.of("id", "nombre", "duracion_min", "activa", "visible_publica"),
                        List.of(List.of(prestacion, "Consulta", 30, true, true))),
                new ExportadorFalso("disponibilidad", List.of("dia_semana"), List.of(List.of(1))),
                new ExportadorFalso("bloqueos", List.of("id", "inicio"), List.of()),
                new ExportadorFalso("pacientes",
                        List.of("id", "nombre", "apellido", "dni", "telefono_e164", "email", "requiere_revision"),
                        List.of(Arrays.asList(paciente, "Pacienta", "Ñandú", "10000001", "+5491100000001", null, false))),
                new ExportadorFalso("turnos",
                        List.of("id", "paciente_id", "tipo_prestacion_id", "inicio", "estado", "origen", "es_sobreturno"),
                        List.of(List.of(UUID.randomUUID(), paciente, prestacion, inicio, "RESERVADO", "LINK", false))),
                new ExportadorFalso("auditoria", List.of("id", "ocurrido_at", "accion"),
                        List.of(List.of(UUID.randomUUID(), inicio, "TURNO_CREADO"))));
    }

    /** Con {@code filas} nulas, falla al leer. */
    private record ExportadorFalso(String entidad, List<String> columnas, List<List<?>> filas)
            implements ExportadorDeEntidad {

        @Override
        public long exportar(ConsultorioId consultorio, ConsumidorDeFila consumidor) throws IOException {
            if (filas == null) {
                throw new IOException("Error de lectura");
            }
            for (List<?> fila : filas) {
                consumidor.aceptar(new ArrayList<>(fila));
            }
            return filas.size();
        }
    }

    private static final class ConsultaFalsa implements ConsultaDeAuditoria {

        private Instant ultima;
        private boolean falla;

        @Override
        public Map<UUID, EventoRegistrado> buscarUltimosPorActor(ConsultorioId consultorio, String entidad,
                Collection<UUID> entidades, ActorTipo actor) {
            return Map.of();
        }

        @Override
        public Optional<EventoRegistrado> buscarUltimo(ConsultorioId consultorio, String entidad, UUID entidadId,
                ActorTipo actor, String accion) {
            if (falla) {
                throw new IllegalStateException("Sin base");
            }
            return Optional.ofNullable(ultima).map(at -> new EventoRegistrado(accion, at, null, null));
        }
    }

    private static final class TransaccionesFalsas extends AbstractPlatformTransactionManager {

        private final List<TransactionDefinition> definiciones = new ArrayList<>();

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaccion, TransactionDefinition definicion) {
            definiciones.add(definicion);
        }

        @Override
        protected void doCommit(DefaultTransactionStatus estado) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus estado) {
        }
    }
}
