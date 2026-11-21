package ar.sistemaodontologico.exportacion.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Formato CSV de la exportación (C-19, design D3 y D5): RFC 4180, UTF-8 sin BOM y escape de fórmulas. */
class EscritorCsvTest {

    private final ByteArrayOutputStream salida = new ByteArrayOutputStream();

    @Test
    void entidadVaciaTieneSoloElEncabezado() throws IOException {
        escribir(List.of("id", "nombre"));

        assertThat(texto()).isEqualTo("id,nombre\r\n");
    }

    @Test
    void cadaFilaTerminaConCrLf() throws IOException {
        escribir(List.of("a", "b"), List.of("uno", "dos"), List.of("tres", "cuatro"));

        assertThat(texto()).isEqualTo("a,b\r\nuno,dos\r\ntres,cuatro\r\n");
    }

    @Test
    void escribeUtf8SinBom() throws IOException {
        escribir(List.of("apellido"), List.of("Ñandú"));

        assertThat(salida.toByteArray()).startsWith("apellido".getBytes(StandardCharsets.UTF_8));
        assertThat(texto()).contains("Ñandú");
    }

    @Test
    void entrecomillaComasComillasYSaltosDeLinea() throws IOException {
        escribir(List.of("a", "b", "c"), List.of("uno, dos", "di \"hola\"", "línea 1\nlínea 2"));

        assertThat(texto()).endsWith("\"uno, dos\",\"di \"\"hola\"\"\",\"línea 1\nlínea 2\"\r\n");
    }

    @Test
    void nombreConFormulaSeEscapaYSeEntrecomilla() throws IOException {
        escribir(List.of("nombre"), List.of("=HIPERVINCULO(\"x\")"));

        assertThat(texto()).isEqualTo("nombre\r\n\"'=HIPERVINCULO(\"\"x\"\")\"\r\n");
    }

    @ParameterizedTest
    @ValueSource(strings = {"=1+1", "+5491100000001", "-2", "@SUMA", "\tx", "\rx"})
    void textoQueEmpiezaConCaracterDeFormulaLlevaComillaSimple(String valor) throws IOException {
        escribir(List.of("celda"), List.of(valor));

        String celda = texto().substring("celda\r\n".length());
        assertThat(celda.startsWith("'") || celda.startsWith("\"'")).isTrue();
    }

    @Test
    void textoComunNoSeEscapa() throws IOException {
        escribir(List.of("celda"), List.of("Paciente-Uno"));

        assertThat(texto()).isEqualTo("celda\r\nPaciente-Uno\r\n");
    }

    @Test
    void numerosYBooleanosNoSeEscapan() throws IOException {
        escribir(List.of("n", "d", "b"), List.of(-5, new BigDecimal("-1.50"), true));

        assertThat(texto()).endsWith("-5,-1.50,true\r\n");
    }

    @Test
    void instantesEnIso8601UtcEIdsEnTexto() throws IOException {
        UUID id = UUID.fromString("00000000-0000-7000-8000-000000000001");

        escribir(List.of("id", "at"), List.of(id, Instant.parse("2026-11-10T13:00:00Z")));

        assertThat(texto()).endsWith("00000000-0000-7000-8000-000000000001,2026-11-10T13:00:00Z\r\n");
    }

    @Test
    void nuloEsCeldaVacia() throws IOException {
        escribir(List.of("a", "b"), Arrays.asList(null, "x"));

        assertThat(texto()).endsWith(",x\r\n");
    }

    @Test
    void terminarNoCierraLaSalidaPorqueEsUnaEntradaDelZip() throws IOException {
        boolean[] cerrada = {false};
        OutputStream zip = new java.io.FilterOutputStream(salida) {
            @Override
            public void close() {
                cerrada[0] = true;
            }
        };

        EscritorCsv escritor = new EscritorCsv(zip, List.of("a"));
        escritor.fila(List.of("x"));
        escritor.terminar();

        assertThat(cerrada[0]).isFalse();
        assertThat(texto()).isEqualTo("a\r\nx\r\n");
    }

    @SafeVarargs
    private void escribir(List<String> columnas, List<?>... filas) throws IOException {
        EscritorCsv escritor = new EscritorCsv(salida, columnas);
        for (List<?> fila : filas) {
            escritor.fila(fila);
        }
        escritor.terminar();
    }

    private String texto() {
        return salida.toString(StandardCharsets.UTF_8);
    }
}
