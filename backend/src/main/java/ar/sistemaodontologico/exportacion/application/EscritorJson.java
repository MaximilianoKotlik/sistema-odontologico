package ar.sistemaodontologico.exportacion.application;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.util.List;

/**
 * Escribe una entidad como un arreglo JSON, fila por fila, sin cargarla en memoria (C-19, design D3). Conserva los
 * valores exactos: el escape de fórmulas es solo del CSV (design D5). No cierra la salida, que es una entrada del ZIP.
 */
public final class EscritorJson {

    private final JsonGenerator generador;
    private final List<String> columnas;

    public EscritorJson(OutputStream salida, ObjectMapper mapper, List<String> columnas) throws IOException {
        this.columnas = List.copyOf(columnas);
        this.generador = mapper.getFactory().createGenerator(salida, JsonEncoding.UTF8);
        this.generador.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
        this.generador.writeStartArray();
    }

    public void fila(List<?> valores) throws IOException {
        if (valores.size() != columnas.size()) {
            throw new IllegalArgumentException("La fila no tiene la misma cantidad de valores que de columnas");
        }
        generador.writeStartObject();
        for (int i = 0; i < columnas.size(); i++) {
            generador.writeFieldName(columnas.get(i));
            valor(valores.get(i));
        }
        generador.writeEndObject();
    }

    /** Cierra el arreglo y vuelca lo pendiente sin cerrar la salida. */
    public void terminar() throws IOException {
        generador.writeEndArray();
        generador.close();
    }

    private void valor(Object valor) throws IOException {
        switch (valor) {
            case null -> generador.writeNull();
            case Boolean booleano -> generador.writeBoolean(booleano);
            case Integer entero -> generador.writeNumber(entero);
            case Long largo -> generador.writeNumber(largo);
            case Short corto -> generador.writeNumber(corto);
            case BigDecimal decimal -> generador.writeNumber(decimal);
            case JsonNode nodo -> generador.writeTree(nodo);
            // Texto, ids, instantes ISO-8601 UTC, fechas y horas.
            default -> generador.writeString(valor.toString());
        }
    }
}
