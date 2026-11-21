package ar.sistemaodontologico.exportacion.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Formato JSON de la exportación (C-19, design D3): arreglo escrito fila por fila. */
class EscritorJsonTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ByteArrayOutputStream salida = new ByteArrayOutputStream();

    @Test
    void entidadVaciaEsUnArregloVacio() throws IOException {
        EscritorJson escritor = new EscritorJson(salida, mapper, List.of("id"));
        escritor.terminar();

        assertThat(salida.toString(java.nio.charset.StandardCharsets.UTF_8)).isEqualTo("[]");
    }

    @Test
    void cadaFilaEsUnObjetoConSusColumnas() throws IOException {
        EscritorJson escritor = new EscritorJson(salida, mapper, List.of("nombre", "edad", "activa"));
        escritor.fila(List.of("Uno", 30, true));
        escritor.fila(List.of("Dos", 40, false));
        escritor.terminar();

        JsonNode filas = mapper.readTree(salida.toByteArray());
        assertThat(filas).hasSize(2);
        assertThat(filas.get(0).get("nombre").asText()).isEqualTo("Uno");
        assertThat(filas.get(1).get("edad").asInt()).isEqualTo(40);
        assertThat(filas.get(1).get("activa").asBoolean()).isFalse();
    }

    @Test
    void conservaElValorExactoDeUnaFormula() throws IOException {
        EscritorJson escritor = new EscritorJson(salida, mapper, List.of("nombre"));
        escritor.fila(List.of("=HIPERVINCULO(\"x\")"));
        escritor.terminar();

        assertThat(mapper.readTree(salida.toByteArray()).get(0).get("nombre").asText())
                .isEqualTo("=HIPERVINCULO(\"x\")");
    }

    @Test
    void instantesEnIso8601UtcIdsEnTextoYDecimalesConPunto() throws IOException {
        UUID id = UUID.fromString("00000000-0000-7000-8000-000000000001");
        EscritorJson escritor = new EscritorJson(salida, mapper, List.of("id", "at", "precio"));
        escritor.fila(List.of(id, Instant.parse("2026-11-10T13:00:00Z"), new BigDecimal("1500.50")));
        escritor.terminar();

        JsonNode fila = mapper.readTree(salida.toByteArray()).get(0);
        assertThat(fila.get("id").asText()).isEqualTo(id.toString());
        assertThat(fila.get("at").asText()).isEqualTo("2026-11-10T13:00:00Z");
        assertThat(fila.get("precio").decimalValue()).isEqualByComparingTo("1500.50");
    }

    @Test
    void jsonbSeEscribeComoObjetoYNoComoTexto() throws IOException {
        JsonNode valor = mapper.readTree("{\"estado\":\"CONFIRMADO\"}");
        EscritorJson escritor = new EscritorJson(salida, mapper, List.of("valor_nuevo"));
        escritor.fila(List.of(valor));
        escritor.terminar();

        JsonNode celda = mapper.readTree(salida.toByteArray()).get(0).get("valor_nuevo");
        assertThat(celda.isObject()).isTrue();
        assertThat(celda.get("estado").asText()).isEqualTo("CONFIRMADO");
    }

    @Test
    void nuloEsNullJson() throws IOException {
        EscritorJson escritor = new EscritorJson(salida, mapper, List.of("email"));
        escritor.fila(Arrays.asList((Object) null));
        escritor.terminar();

        assertThat(mapper.readTree(salida.toByteArray()).get(0).get("email").isNull()).isTrue();
    }

    @Test
    void terminarNoCierraLaSalidaPorqueEsUnaEntradaDelZip() throws IOException {
        boolean[] cerrada = {false};
        OutputStream zip = new FilterOutputStream(salida) {
            @Override
            public void close() {
                cerrada[0] = true;
            }
        };

        EscritorJson escritor = new EscritorJson(zip, mapper, List.of("a"));
        escritor.fila(List.of("x"));
        escritor.terminar();

        assertThat(cerrada[0]).isFalse();
        assertThat(mapper.readTree(salida.toByteArray())).hasSize(1);
    }
}
