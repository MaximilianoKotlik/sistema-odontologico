package ar.sistemaodontologico.exportacion.application;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Escribe una entidad en CSV RFC 4180, UTF-8 sin BOM y fin de línea {@code \r\n} (C-19, design D3). Toda celda de texto
 * que una planilla pueda interpretar como fórmula lleva una comilla simple inicial (design D5): nombres y emails llegan
 * de un formulario público. No cierra la salida, que es una entrada del ZIP.
 */
public final class EscritorCsv {

    private static final String FIN_DE_LINEA = "\r\n";
    private static final String INICIOS_DE_FORMULA = "=+-@\t\r";

    private final Writer salida;

    public EscritorCsv(OutputStream salida, List<String> columnas) throws IOException {
        this.salida = new BufferedWriter(new OutputStreamWriter(salida, StandardCharsets.UTF_8));
        fila(columnas);
    }

    public void fila(List<?> valores) throws IOException {
        for (int i = 0; i < valores.size(); i++) {
            if (i > 0) {
                salida.write(',');
            }
            salida.write(celda(valores.get(i)));
        }
        salida.write(FIN_DE_LINEA);
    }

    /** Vuelca lo pendiente sin cerrar la salida. */
    public void terminar() throws IOException {
        salida.flush();
    }

    private static String celda(Object valor) {
        if (valor == null) {
            return "";
        }
        // Solo el texto se escapa: números, booleanos, instantes e ids los genera el sistema (design D5).
        String texto = valor instanceof CharSequence ? sinFormula(valor.toString()) : valor.toString();
        return necesitaComillas(texto) ? '"' + texto.replace("\"", "\"\"") + '"' : texto;
    }

    private static String sinFormula(String texto) {
        boolean esFormula = !texto.isEmpty() && INICIOS_DE_FORMULA.indexOf(texto.charAt(0)) >= 0;
        return esFormula ? "'" + texto : texto;
    }

    private static boolean necesitaComillas(String texto) {
        return texto.indexOf(',') >= 0 || texto.indexOf('"') >= 0 || texto.indexOf('\n') >= 0 || texto.indexOf('\r') >= 0;
    }
}
