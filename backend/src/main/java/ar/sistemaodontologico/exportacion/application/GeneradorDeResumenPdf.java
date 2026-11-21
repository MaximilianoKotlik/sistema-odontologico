package ar.sistemaodontologico.exportacion.application;

import ar.sistemaodontologico.exportacion.application.DatosDelResumen.PacienteResumen;
import ar.sistemaodontologico.exportacion.application.DatosDelResumen.PrestacionResumen;
import ar.sistemaodontologico.exportacion.application.DatosDelResumen.TurnoResumen;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.ColumnText;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfPageEventHelper;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

/**
 * Arma {@code resumen.pdf} (C-19, design D6) con Noto Sans embebida, para que la ñ, las tildes y cualquier nombre se
 * vean bien. Las fechas van en la zona del consultorio. No cierra la salida, que es una entrada del ZIP.
 */
@Component
public class GeneradorDeResumenPdf {

    private static final String FUENTE_REGULAR = "/fonts/NotoSans-Regular.ttf";
    private static final String FUENTE_NEGRITA = "/fonts/NotoSans-Bold.ttf";
    private static final float CUERPO = 9f;
    private static final float SUBTITULO = 13f;
    private static final float TITULO = 18f;
    private static final float PIE = 8f;
    private static final float MARGEN = 36f;
    private static final float ESPACIO = 12f;

    public void generar(DatosDelResumen datos, OutputStream salida) throws IOException {
        BaseFont regular = fuente(FUENTE_REGULAR);
        BaseFont negrita = fuente(FUENTE_NEGRITA);
        Font texto = new Font(regular, CUERPO);
        Font encabezado = new Font(negrita, CUERPO);
        Font subtitulo = new Font(negrita, SUBTITULO);
        DateTimeFormatter fechaHora = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(datos.zona());
        DateTimeFormatter fecha = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(datos.zona());

        Document documento = new Document(PageSize.A4, MARGEN, MARGEN, MARGEN, MARGEN + ESPACIO);
        PdfWriter escritor = PdfWriter.getInstance(documento, salida);
        escritor.setCloseStream(false);
        escritor.setPageEvent(new Pie(new Font(regular, PIE), datos.versionEsquema()));
        documento.open();

        documento.add(new Paragraph("Exportación de datos", new Font(negrita, TITULO)));
        seccion(documento, "Consultorio", subtitulo);
        documento.add(new Paragraph(datos.consultorio(), texto));
        documento.add(new Paragraph("Dirección: " + vacioSiNulo(datos.direccion()), texto));
        documento.add(new Paragraph("Teléfono: " + vacioSiNulo(datos.telefono()), texto));
        documento.add(new Paragraph("Zona horaria: " + datos.zona().getId(), texto));
        documento.add(new Paragraph("Generado: " + fechaHora.format(datos.generadoAt()), texto));

        seccion(documento, "Prestaciones (" + datos.prestaciones().size() + ")", subtitulo);
        tabla(documento, texto, encabezado, List.of("Nombre", "Duración", "Activa", "Visible"),
                datos.prestaciones().stream().map(GeneradorDeResumenPdf::fila).toList());

        seccion(documento, "Pacientes (" + datos.pacientes().size() + ")", subtitulo);
        tabla(documento, texto, encabezado, List.of("Apellido y nombre", "DNI", "Teléfono", "Email", "Revisión"),
                datos.pacientes().stream().map(GeneradorDeResumenPdf::fila).toList());

        seccion(documento, "Turnos (" + datos.turnos().size() + ")", subtitulo);
        tabla(documento, texto, encabezado, List.of("Fecha y hora", "Paciente", "Prestación", "Estado", "Origen",
                "Sobreturno"), datos.turnos().stream().map(turno -> fila(turno, fechaHora)).toList());

        seccion(documento, "Auditoría", subtitulo);
        Map<String, Long> porAccion = new TreeMap<>(datos.auditoria().porAccion());
        tabla(documento, texto, encabezado, List.of("Acción", "Cantidad"), porAccion.entrySet().stream()
                .map(entrada -> List.of(entrada.getKey(), entrada.getValue().toString())).toList());
        documento.add(new Paragraph("Primer evento: " + instante(datos.auditoria().primero(), fecha)
                + " · Último evento: " + instante(datos.auditoria().ultimo(), fecha), texto));

        documento.close();
        salida.flush();
    }

    private static BaseFont fuente(String ruta) throws IOException {
        try (InputStream entrada = GeneradorDeResumenPdf.class.getResourceAsStream(ruta)) {
            if (entrada == null) {
                throw new IllegalStateException("Falta la fuente del resumen: " + ruta);
            }
            return BaseFont.createFont(ruta.substring(ruta.lastIndexOf('/') + 1), BaseFont.IDENTITY_H, BaseFont.EMBEDDED,
                    false, entrada.readAllBytes(), null);
        }
    }

    private static void seccion(Document documento, String titulo, Font fuente) {
        Paragraph parrafo = new Paragraph(titulo, fuente);
        parrafo.setSpacingBefore(ESPACIO);
        parrafo.setSpacingAfter(ESPACIO / 2);
        documento.add(parrafo);
    }

    private static void tabla(Document documento, Font texto, Font encabezado, List<String> columnas,
            List<List<String>> filas) {
        if (filas.isEmpty()) {
            documento.add(new Paragraph("Sin registros.", texto));
            return;
        }
        PdfPTable tabla = new PdfPTable(columnas.size());
        tabla.setWidthPercentage(100);
        tabla.setHeaderRows(1);
        columnas.forEach(columna -> tabla.addCell(new Phrase(columna, encabezado)));
        filas.forEach(fila -> fila.forEach(celda -> tabla.addCell(new Phrase(celda, texto))));
        documento.add(tabla);
    }

    private static List<String> fila(PrestacionResumen prestacion) {
        return List.of(prestacion.nombre(), prestacion.duracionMin() + " min", siNo(prestacion.activa()),
                siNo(prestacion.visible()));
    }

    private static List<String> fila(PacienteResumen paciente) {
        return List.of(paciente.apellido() + ", " + paciente.nombre(), paciente.dni(), paciente.telefono(),
                vacioSiNulo(paciente.email()), paciente.requiereRevision() ? "Pendiente" : "No");
    }

    private static List<String> fila(TurnoResumen turno, DateTimeFormatter fechaHora) {
        return List.of(fechaHora.format(turno.inicio()), turno.paciente(), turno.prestacion(), turno.estado(),
                turno.origen(), siNo(turno.sobreturno()));
    }

    private static String instante(Instant valor, DateTimeFormatter formato) {
        return valor == null ? "-" : formato.format(valor);
    }

    private static String siNo(boolean valor) {
        return valor ? "Sí" : "No";
    }

    private static String vacioSiNulo(String valor) {
        return valor == null ? "-" : valor;
    }

    /** Versión del esquema y número de página (design D6). */
    private static final class Pie extends PdfPageEventHelper {

        private final Font fuente;
        private final String version;

        private Pie(Font fuente, String version) {
            this.fuente = fuente;
            this.version = version;
        }

        @Override
        public void onEndPage(PdfWriter escritor, Document documento) {
            ColumnText.showTextAligned(escritor.getDirectContent(), Element.ALIGN_CENTER,
                    new Phrase("Esquema " + version + " · Página " + escritor.getPageNumber(), fuente),
                    (documento.left() + documento.right()) / 2, documento.bottom() - ESPACIO, 0);
        }
    }
}
