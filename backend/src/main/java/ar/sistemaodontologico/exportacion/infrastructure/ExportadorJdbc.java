package ar.sistemaodontologico.exportacion.infrastructure;

import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import ar.sistemaodontologico.exportacion.application.ExportadorDeEntidad;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Base de los exportadores (C-19, design D2 y D3): columnas explícitas, filtro por consultorio y lectura con
 * {@code fetchSize}, que dentro de la transacción del streaming no carga toda la tabla en memoria.
 */
abstract class ExportadorJdbc implements ExportadorDeEntidad {

    private static final int FILAS_POR_LECTURA = 500;

    private final JdbcClient jdbc;
    private final ObjectMapper json;
    private final String entidad;
    private final List<String> columnas;
    private final String consulta;

    ExportadorJdbc(DataSource dataSource, ObjectMapper json, String entidad, String tabla, String columnaTenant,
            String orden, List<String> columnas) {
        // Plantilla propia: el fetchSize no debe alcanzar al resto de las consultas de la aplicación.
        JdbcTemplate plantilla = new JdbcTemplate(dataSource);
        plantilla.setFetchSize(FILAS_POR_LECTURA);
        this.jdbc = JdbcClient.create(plantilla);
        this.json = json;
        this.entidad = entidad;
        this.columnas = List.copyOf(columnas);
        this.consulta = "SELECT " + String.join(", ", columnas) + " FROM " + tabla + " WHERE " + columnaTenant
                + " = :consultorio ORDER BY " + orden;
    }

    @Override
    public final String entidad() {
        return entidad;
    }

    @Override
    public final List<String> columnas() {
        return columnas;
    }

    @Override
    public final long exportar(ConsultorioId consultorio, ConsumidorDeFila consumidor) throws IOException {
        long[] cantidad = {0};
        try {
            jdbc.sql(consulta)
                    .param("consultorio", consultorio.valor())
                    .query((ResultSet fila) -> {
                        try {
                            consumidor.aceptar(valores(fila));
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                        cantidad[0]++;
                    });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
        return cantidad[0];
    }

    private List<Object> valores(ResultSet fila) throws SQLException {
        ResultSetMetaData metadatos = fila.getMetaData();
        List<Object> valores = new ArrayList<>(columnas.size());
        for (int i = 1; i <= columnas.size(); i++) {
            valores.add(valor(fila, i, metadatos.getColumnTypeName(i)));
        }
        return valores;
    }

    private Object valor(ResultSet fila, int columna, String tipo) throws SQLException {
        return switch (tipo) {
            case "timestamptz" -> {
                Timestamp instante = fila.getTimestamp(columna);
                yield instante == null ? null : instante.toInstant();
            }
            case "jsonb" -> jsonb(fila.getString(columna));
            case "uuid", "bool", "int2", "int4", "int8", "numeric" -> fila.getObject(columna);
            // varchar, citext, inet, date y time: su forma de texto es la que se exporta.
            default -> fila.getString(columna);
        };
    }

    private Object jsonb(String valor) {
        if (valor == null) {
            return null;
        }
        try {
            return json.readTree(valor);
        } catch (JsonProcessingException e) {
            // Sin la causa ni el valor: puede contener datos personales.
            throw new IllegalStateException("No se pudo leer un valor JSON de la entidad " + entidad);
        }
    }
}
