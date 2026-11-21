package ar.sistemaodontologico.exportacion.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

/** Manifest del ZIP (C-19, design D3): cantidades, y hash y bytes calculados mientras se escribe. */
class ManifestDeExportacionTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ManifestDeExportacion manifest = new ManifestDeExportacion(
            Instant.parse("2026-11-10T13:00:00Z"), "consultorio-prueba", "America/Argentina/Buenos_Aires");

    @Test
    void registraHashYBytesDeCadaArchivoYDejaPasarElContenido() throws IOException, NoSuchAlgorithmException {
        ByteArrayOutputStream destino = new ByteArrayOutputStream();
        byte[] contenido = "id,nombre\r\n1,Ñandú\r\n".getBytes(StandardCharsets.UTF_8);

        try (OutputStream entrada = manifest.medir("turnos.csv", destino)) {
            entrada.write(contenido, 0, 5);
            entrada.write(contenido, 5, contenido.length - 5);
        }

        assertThat(destino.toByteArray()).isEqualTo(contenido);
        JsonNode archivo = leido().get("archivos").get(0);
        assertThat(archivo.get("nombre").asText()).isEqualTo("turnos.csv");
        assertThat(archivo.get("bytes").asLong()).isEqualTo(contenido.length);
        assertThat(archivo.get("sha256").asText()).isEqualTo(sha256(contenido));
    }

    @Test
    void cerrarLaMedicionNoCierraElZip() throws IOException {
        boolean[] cerrado = {false};
        OutputStream zip = new FilterOutputStream(new ByteArrayOutputStream()) {
            @Override
            public void close() {
                cerrado[0] = true;
            }
        };

        manifest.medir("a.csv", zip).close();

        assertThat(cerrado[0]).isFalse();
    }

    @Test
    void cerrarDosVecesNoDuplicaElArchivo() throws IOException {
        OutputStream entrada = manifest.medir("a.csv", new ByteArrayOutputStream());
        entrada.close();
        entrada.close();

        assertThat(leido().get("archivos")).hasSize(1);
    }

    @Test
    void incluyeEsquemaInstanteSlugZonaYCantidades() throws IOException {
        manifest.contar("pacientes", 5);
        manifest.contar("turnos", 10);

        JsonNode json = leido();

        assertThat(json.get("versionEsquema").asText()).isEqualTo("1");
        assertThat(json.get("generadoAt").asText()).isEqualTo("2026-11-10T13:00:00Z");
        assertThat(json.get("consultorio").get("slug").asText()).isEqualTo("consultorio-prueba");
        assertThat(json.get("zonaHoraria").asText()).isEqualTo("America/Argentina/Buenos_Aires");
        assertThat(json.get("entidades").get("pacientes").asLong()).isEqualTo(5);
        assertThat(json.get("entidades").get("turnos").asLong()).isEqualTo(10);
    }

    @Test
    void exponeLasCantidadesParaElEventoDeAuditoria() {
        manifest.contar("turnos", 10);

        assertThat(manifest.cantidades()).containsEntry("turnos", 10L);
    }

    private JsonNode leido() throws IOException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        manifest.escribirEn(salida, mapper);
        return mapper.readTree(salida.toByteArray());
    }

    private static String sha256(byte[] contenido) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenido));
    }
}
