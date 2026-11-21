package ar.sistemaodontologico.exportacion.application;

import static org.assertj.core.api.Assertions.assertThat;

import ar.sistemaodontologico.exportacion.application.DatosDelResumen.AuditoriaResumen;
import ar.sistemaodontologico.exportacion.application.DatosDelResumen.PacienteResumen;
import ar.sistemaodontologico.exportacion.application.DatosDelResumen.PrestacionResumen;
import ar.sistemaodontologico.exportacion.application.DatosDelResumen.TurnoResumen;
import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

/** Resumen PDF de la exportación (C-19, tarea 4.1, design D6), con el texto extraído por PDFBox. */
class GeneradorDeResumenPdfTest {

    private static final Instant TURNO = Instant.parse("2030-03-04T13:00:00Z");

    private final GeneradorDeResumenPdf generador = new GeneradorDeResumenPdf();

    @Test
    void tieneLasSeccionesMinimasConSusCantidades() throws IOException {
        String texto = texto(datos());

        assertThat(texto).contains("Consultorio", "Prestaciones (1)", "Pacientes (1)", "Turnos (1)", "Auditoría");
    }

    @Test
    void conservaLosCaracteresEnEspanol() throws IOException {
        assertThat(texto(datos())).contains("Ñandú Pérez");
    }

    @Test
    void muestraLasFechasEnLaZonaDelConsultorio() throws IOException {
        assertThat(texto(datos())).contains("04/03/2030 10:00");
    }

    @Test
    void resumeLaAuditoriaPorAccionYRango() throws IOException {
        assertThat(texto(datos())).contains("TURNO_CREADO", "3", "01/03/2030", "02/03/2030");
    }

    @Test
    void entidadesVaciasNoRompenElDocumento() throws IOException {
        DatosDelResumen vacio = new DatosDelResumen("Consultorio de prueba", null, null,
                ZoneId.of("America/Argentina/Buenos_Aires"), TURNO, "1", List.of(), List.of(), List.of(),
                new AuditoriaResumen(Map.of(), null, null));

        assertThat(texto(vacio)).contains("Prestaciones (0)", "Pacientes (0)", "Turnos (0)");
    }

    @Test
    void llevaVersionDelEsquemaYNumeroDePagina() throws IOException {
        assertThat(texto(datos())).contains("Esquema 1", "Página 1");
    }

    @Test
    void noCierraLaSalidaPorqueEsUnaEntradaDelZip() throws IOException {
        boolean[] cerrada = {false};
        ByteArrayOutputStream destino = new ByteArrayOutputStream();
        OutputStream zip = new FilterOutputStream(destino) {
            @Override
            public void write(byte[] contenido, int desde, int cantidad) throws IOException {
                out.write(contenido, desde, cantidad);
            }

            @Override
            public void close() {
                cerrada[0] = true;
            }
        };

        generador.generar(datos(), zip);

        assertThat(cerrada[0]).isFalse();
        assertThat(destino.size()).isPositive();
    }

    private DatosDelResumen datos() {
        return new DatosDelResumen("Consultorio de prueba", "Calle Ficticia 123", "+5491100000001",
                ZoneId.of("America/Argentina/Buenos_Aires"), Instant.parse("2030-03-05T12:00:00Z"), "1",
                List.of(new PrestacionResumen("Consulta", 30, true, true)),
                List.of(new PacienteResumen("Ñandú Pérez", "Pacienta", "10000001", "+5491100000002",
                        "paciente@example.com", false)),
                List.of(new TurnoResumen(TURNO, "Pacienta Ñandú Pérez", "Consulta", "RESERVADO", "LINK", false)),
                new AuditoriaResumen(Map.of("TURNO_CREADO", 3L), Instant.parse("2030-03-01T15:00:00Z"),
                        Instant.parse("2030-03-02T15:00:00Z")));
    }

    private String texto(DatosDelResumen datos) throws IOException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        generador.generar(datos, salida);
        try (PDDocument documento = Loader.loadPDF(salida.toByteArray())) {
            return new PDFTextStripper().getText(documento);
        }
    }
}
