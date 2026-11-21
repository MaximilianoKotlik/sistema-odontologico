package ar.sistemaodontologico.exportacion.application;

import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import java.io.IOException;
import java.util.List;

/**
 * Lee una entidad del consultorio por streaming, con sus columnas declaradas una por una (C-19, design D3): una columna
 * nueva con un secreto no entra sola.
 */
public interface ExportadorDeEntidad {

    /** Recibe cada fila, con los valores en el orden de {@link #columnas()}. */
    @FunctionalInterface
    interface ConsumidorDeFila {
        void aceptar(List<Object> valores) throws IOException;
    }

    /** Nombre de la entidad y de sus archivos en el ZIP. */
    String entidad();

    /** Lista blanca de columnas. */
    List<String> columnas();

    /** Recorre las filas del consultorio y devuelve cuántas fueron. */
    long exportar(ConsultorioId consultorio, ConsumidorDeFila consumidor) throws IOException;
}
