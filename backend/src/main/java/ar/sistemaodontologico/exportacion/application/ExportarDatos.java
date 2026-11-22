package ar.sistemaodontologico.exportacion.application;

import ar.sistemaodontologico.compartido.application.ConsultaDeAuditoria;
import ar.sistemaodontologico.compartido.application.PublicadorDeAuditoria;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import ar.sistemaodontologico.compartido.domain.auditoria.Actor;
import ar.sistemaodontologico.compartido.domain.auditoria.ActorTipo;
import ar.sistemaodontologico.compartido.domain.auditoria.CambioAuditado;
import ar.sistemaodontologico.exportacion.application.DatosDelResumen.AuditoriaResumen;
import ar.sistemaodontologico.exportacion.application.DatosDelResumen.PacienteResumen;
import ar.sistemaodontologico.exportacion.application.DatosDelResumen.PrestacionResumen;
import ar.sistemaodontologico.exportacion.application.DatosDelResumen.TurnoResumen;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Exportación completa del consultorio (C-19, US-050, FL-06). {@link #reservar} corre en el hilo de la petición y decide
 * el 429; {@link #generar} corre en el hilo del streaming, con todo lo que necesita en el pedido (design D2). Toda
 * reserva permitida se libera con {@link #generar} o, si el streaming no llega a empezar, con {@link #liberar}.
 */
@Service
public class ExportarDatos {

    /** Resultado de pedir una exportación. */
    public record Reserva(boolean permitida, long reintentarEnSegundos, String nombreArchivo) {

        private static Reserva rechazada(long segundos) {
            return new Reserva(false, segundos, null);
        }
    }

    @FunctionalInterface
    private interface Contenido {
        void escribir(OutputStream salida) throws IOException;
    }

    static final String ACCION = "EXPORTACION_GENERADA";

    private static final Logger LOG = LoggerFactory.getLogger(ExportarDatos.class);
    private static final Duration ESPERA = Duration.ofMinutes(5);
    private static final long SEGUNDOS_CON_OTRA_EN_CURSO = 30;
    private static final String ENTIDAD_CONSULTORIO = "consultorio";
    private static final List<String> ENTIDADES = List.of(ENTIDAD_CONSULTORIO, "odontologo", "prestaciones",
            "disponibilidad", "bloqueos", "pacientes", "turnos", "auditoria");
    private static final String LEEME = """
            Exportación de los datos de tu consultorio

            Cada entidad está en dos formatos: JSON y CSV (UTF-8, separado por comas).
            resumen.pdf es una versión para leer sin ningún sistema.
            manifest.json indica las cantidades y el SHA-256 de cada archivo.

            Las fechas de los JSON y CSV están en UTC (ISO-8601). Las del PDF, en la zona de tu consultorio.
            En los CSV, los textos que una planilla podría tomar como fórmula empiezan con una comilla simple.

            La importación de estos archivos a otros sistemas no está garantizada.
            """;

    private final Map<String, ExportadorDeEntidad> exportadores;
    private final GeneradorDeResumenPdf resumen;
    private final GuardiaDeExportaciones guardia;
    private final CandadoDeExportacion candado;
    private final ConsultaDeAuditoria consultaDeAuditoria;
    private final PublicadorDeAuditoria auditoria;
    private final TransactionTemplate lectura;
    private final TransactionTemplate escritura;
    private final ObjectMapper mapper;
    private final Clock clock;

    public ExportarDatos(List<ExportadorDeEntidad> exportadores, GeneradorDeResumenPdf resumen,
            GuardiaDeExportaciones guardia, CandadoDeExportacion candado, ConsultaDeAuditoria consultaDeAuditoria,
            PublicadorDeAuditoria auditoria, PlatformTransactionManager transacciones, ObjectMapper mapper,
            Clock clock) {
        this.exportadores = exportadores.stream()
                .collect(Collectors.toMap(ExportadorDeEntidad::entidad, Function.identity()));
        this.resumen = resumen;
        this.guardia = guardia;
        this.candado = candado;
        this.consultaDeAuditoria = consultaDeAuditoria;
        this.auditoria = auditoria;
        this.lectura = new TransactionTemplate(transacciones);
        this.lectura.setReadOnly(true);
        this.lectura.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        this.escritura = new TransactionTemplate(transacciones);
        this.escritura.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.mapper = mapper;
        this.clock = clock;
    }

    /** Toma la guardia y aplica el límite de una exportación cada 5 minutos (design D7). */
    public Reserva reservar(PedidoDeExportacion pedido) {
        ConsultorioId consultorio = pedido.consultorio();
        if (!guardia.tomar(pedido)) {
            return Reserva.rechazada(SEGUNDOS_CON_OTRA_EN_CURSO);
        }
        try {
            Optional<Long> espera = segundosDeEspera(consultorio);
            if (espera.isPresent()) {
                guardia.liberar(pedido);
                return Reserva.rechazada(espera.get());
            }
            return new Reserva(true, 0, nombreArchivo(pedido));
        } catch (RuntimeException e) {
            guardia.liberar(pedido);
            throw e;
        }
    }

    /** Devuelve la guardia de una reserva cuyo streaming no llegó a empezar. */
    public void liberar(PedidoDeExportacion pedido) {
        guardia.liberar(pedido);
    }

    /**
     * Escribe el ZIP y, si terminó bien, registra el evento. Si falla, no llama a {@code finish()}: el cliente recibe un
     * archivo truncado e inválido y no se consume el límite.
     */
    public void generar(PedidoDeExportacion pedido, OutputStream salida) throws IOException {
        try {
            ManifestDeExportacion manifest = escribirZip(pedido, salida);
            registrarEvento(pedido, manifest);
        } finally {
            guardia.liberar(pedido);
        }
    }

    private Optional<Long> segundosDeEspera(ConsultorioId consultorio) {
        return consultaDeAuditoria
                .buscarUltimo(consultorio, ENTIDAD_CONSULTORIO, consultorio.valor(), ActorTipo.ODONTOLOGO, ACCION)
                .map(evento -> Duration.between(clock.instant(), evento.ocurridoAt().plus(ESPERA)))
                .filter(restante -> restante.isPositive())
                .map(restante -> restante.toSeconds() + (restante.toNanosPart() > 0 ? 1 : 0));
    }

    private String nombreArchivo(PedidoDeExportacion pedido) {
        Map<String, Object> datos = datosDelConsultorio(pedido.consultorio());
        ZoneId zona = ZoneId.of((String) datos.get("zona_horaria"));
        LocalDate fecha = pedido.solicitadoAt().atZone(zona).toLocalDate();
        return "export-" + datos.get("slug") + "-" + fecha + ".zip";
    }

    private ManifestDeExportacion escribirZip(PedidoDeExportacion pedido, OutputStream salida) throws IOException {
        try {
            return lectura.execute(estado -> {
                try {
                    if (!candado.tomar(pedido.consultorio())) {
                        LOG.warn("Exportación cortada: otra instancia ya exporta el consultorio {}",
                                pedido.consultorio());
                        throw new IllegalStateException("Ya hay una exportación en curso");
                    }
                    return escribirEntradas(pedido, salida);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private ManifestDeExportacion escribirEntradas(PedidoDeExportacion pedido, OutputStream salida) throws IOException {
        ConsultorioId consultorio = pedido.consultorio();
        Map<String, Object> datos = datosDelConsultorio(consultorio);
        String zona = (String) datos.get("zona_horaria");
        ManifestDeExportacion manifest =
                new ManifestDeExportacion(pedido.solicitadoAt(), (String) datos.get("slug"), zona);
        // Sin try-with-resources: close() llamaría a finish() y un error dejaría un ZIP que parece válido.
        ZipOutputStream zip = new ZipOutputStream(salida, StandardCharsets.UTF_8);
        entrada(zip, manifest, "LEEME.txt", destino -> destino.write(LEEME.getBytes(StandardCharsets.UTF_8)));
        for (String entidad : ENTIDADES) {
            escribirEntidad(zip, manifest, exportador(entidad), consultorio);
        }
        DatosDelResumen paraElPdf = datosDelResumen(pedido, datos, ZoneId.of(zona));
        entrada(zip, manifest, "resumen.pdf", destino -> resumen.generar(paraElPdf, destino));
        zip.putNextEntry(new ZipEntry("manifest.json"));
        manifest.escribirEn(zip, mapper);
        zip.closeEntry();
        zip.finish();
        zip.flush();
        return manifest;
    }

    private void escribirEntidad(ZipOutputStream zip, ManifestDeExportacion manifest, ExportadorDeEntidad exportador,
            ConsultorioId consultorio) throws IOException {
        long[] cantidad = {0};
        entrada(zip, manifest, exportador.entidad() + ".json", destino -> {
            EscritorJson json = new EscritorJson(destino, mapper, exportador.columnas());
            cantidad[0] = exportador.exportar(consultorio, json::fila);
            json.terminar();
        });
        entrada(zip, manifest, exportador.entidad() + ".csv", destino -> {
            EscritorCsv csv = new EscritorCsv(destino, exportador.columnas());
            exportador.exportar(consultorio, csv::fila);
            csv.terminar();
        });
        manifest.contar(exportador.entidad(), cantidad[0]);
    }

    private static void entrada(ZipOutputStream zip, ManifestDeExportacion manifest, String nombre, Contenido contenido)
            throws IOException {
        zip.putNextEntry(new ZipEntry(nombre));
        try (OutputStream medida = manifest.medir(nombre, zip)) {
            contenido.escribir(medida);
        }
        zip.closeEntry();
    }

    private DatosDelResumen datosDelResumen(PedidoDeExportacion pedido, Map<String, Object> consultorio, ZoneId zona)
            throws IOException {
        ConsultorioId id = pedido.consultorio();
        Map<Object, String> nombresDePrestaciones = new HashMap<>();
        List<PrestacionResumen> prestaciones = new ArrayList<>();
        recorrer("prestaciones", id, fila -> {
            nombresDePrestaciones.put(fila.get("id"), (String) fila.get("nombre"));
            prestaciones.add(new PrestacionResumen((String) fila.get("nombre"), (Integer) fila.get("duracion_min"),
                    (Boolean) fila.get("activa"), (Boolean) fila.get("visible_publica")));
        });
        Map<Object, String> nombresDePacientes = new HashMap<>();
        List<PacienteResumen> pacientes = new ArrayList<>();
        recorrer("pacientes", id, fila -> {
            String nombre = (String) fila.get("nombre");
            String apellido = (String) fila.get("apellido");
            nombresDePacientes.put(fila.get("id"), nombre + " " + apellido);
            pacientes.add(new PacienteResumen(apellido, nombre, (String) fila.get("dni"),
                    (String) fila.get("telefono_e164"), (String) fila.get("email"),
                    (Boolean) fila.get("requiere_revision")));
        });
        List<TurnoResumen> turnos = new ArrayList<>();
        recorrer("turnos", id, fila -> turnos.add(new TurnoResumen((Instant) fila.get("inicio"),
                nombresDePacientes.get(fila.get("paciente_id")), nombresDePrestaciones.get(fila.get("tipo_prestacion_id")),
                (String) fila.get("estado"), (String) fila.get("origen"), (Boolean) fila.get("es_sobreturno"))));
        return new DatosDelResumen((String) consultorio.get("nombre"), (String) consultorio.get("direccion"),
                (String) consultorio.get("telefono_contacto"), zona, pedido.solicitadoAt(),
                ManifestDeExportacion.VERSION_ESQUEMA, prestaciones, pacientes, turnos, resumenDeAuditoria(id));
    }

    private AuditoriaResumen resumenDeAuditoria(ConsultorioId consultorio) throws IOException {
        Map<String, Long> porAccion = new HashMap<>();
        Instant[] rango = new Instant[2];
        recorrer("auditoria", consultorio, fila -> {
            porAccion.merge((String) fila.get("accion"), 1L, Long::sum);
            Instant ocurrido = (Instant) fila.get("ocurrido_at");
            rango[0] = rango[0] == null || ocurrido.isBefore(rango[0]) ? ocurrido : rango[0];
            rango[1] = rango[1] == null || ocurrido.isAfter(rango[1]) ? ocurrido : rango[1];
        });
        return new AuditoriaResumen(porAccion, rango[0], rango[1]);
    }

    private Map<String, Object> datosDelConsultorio(ConsultorioId consultorio) {
        List<Map<String, Object>> filas = new ArrayList<>();
        try {
            recorrer(ENTIDAD_CONSULTORIO, consultorio, filas::add);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (filas.size() != 1) {
            throw new IllegalStateException("El consultorio de la exportación no existe");
        }
        return filas.getFirst();
    }

    @FunctionalInterface
    private interface ConsumidorDeMapa {
        void aceptar(Map<String, Object> fila);
    }

    private void recorrer(String entidad, ConsultorioId consultorio, ConsumidorDeMapa consumidor) throws IOException {
        ExportadorDeEntidad exportador = exportador(entidad);
        List<String> columnas = exportador.columnas();
        exportador.exportar(consultorio, valores -> {
            Map<String, Object> fila = new LinkedHashMap<>();
            for (int i = 0; i < columnas.size(); i++) {
                fila.put(columnas.get(i), valores.get(i));
            }
            consumidor.aceptar(fila);
        });
    }

    private ExportadorDeEntidad exportador(String entidad) {
        ExportadorDeEntidad exportador = exportadores.get(entidad);
        if (exportador == null) {
            throw new IllegalStateException("Falta el exportador de " + entidad);
        }
        return exportador;
    }

    /** Si falla, el ZIP ya se entregó: se loguea sin datos y el límite no cuenta este pedido (design D8, Risks). */
    private void registrarEvento(PedidoDeExportacion pedido, ManifestDeExportacion manifest) {
        UUID consultorio = pedido.consultorio().valor();
        Map<String, Object> valorNuevo = new LinkedHashMap<>();
        valorNuevo.put("versionEsquema", ManifestDeExportacion.VERSION_ESQUEMA);
        valorNuevo.put("entidades", manifest.cantidades());
        CambioAuditado cambio = new CambioAuditado(pedido.consultorio(),
                new Actor(ActorTipo.ODONTOLOGO, pedido.usuarioId()), ENTIDAD_CONSULTORIO, consultorio, ACCION, null,
                valorNuevo, pedido.ip());
        try {
            escritura.executeWithoutResult(estado -> auditoria.publicar(cambio));
        } catch (RuntimeException e) {
            LOG.error("No se pudo registrar {} del consultorio {}", ACCION, consultorio);
        }
    }
}
