package ar.sistemaodontologico.exportacion.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Manifest del ZIP (C-19, design D3): versión del esquema, cantidades por entidad, y SHA-256 y bytes de cada archivo,
 * calculados mientras se escribe para no recorrer dos veces lo que ya salió por la red.
 */
public final class ManifestDeExportacion {

    static final String VERSION_ESQUEMA = "1";

    private final Instant generadoAt;
    private final String slug;
    private final String zonaHoraria;
    private final Map<String, Long> entidades = new LinkedHashMap<>();
    private final List<Archivo> archivos = new ArrayList<>();

    /** Un archivo del ZIP con su huella. */
    public record Archivo(String nombre, String sha256, long bytes) {
    }

    public ManifestDeExportacion(Instant generadoAt, String slug, String zonaHoraria) {
        this.generadoAt = Objects.requireNonNull(generadoAt, "generadoAt");
        this.slug = Objects.requireNonNull(slug, "slug");
        this.zonaHoraria = Objects.requireNonNull(zonaHoraria, "zonaHoraria");
    }

    /** Devuelve una salida que mide lo escrito; al cerrarla registra el archivo, sin cerrar {@code destino}. */
    public OutputStream medir(String nombre, OutputStream destino) {
        return new EntradaMedida(nombre, destino);
    }

    public void contar(String entidad, long cantidad) {
        entidades.put(entidad, cantidad);
    }

    /** Cantidades por entidad, sin datos de pacientes: es lo que guarda el evento de auditoría (design D8). */
    public Map<String, Long> cantidades() {
        return Map.copyOf(entidades);
    }

    public void escribirEn(OutputStream salida, ObjectMapper mapper) throws IOException {
        Map<String, Object> contenido = new LinkedHashMap<>();
        contenido.put("versionEsquema", VERSION_ESQUEMA);
        contenido.put("generadoAt", generadoAt.toString());
        contenido.put("consultorio", Map.of("slug", slug));
        contenido.put("zonaHoraria", zonaHoraria);
        contenido.put("entidades", entidades);
        contenido.put("archivos", archivos);
        salida.write(mapper.writeValueAsBytes(contenido));
    }

    private final class EntradaMedida extends OutputStream {

        private final String nombre;
        private final OutputStream destino;
        private final MessageDigest digest;
        private long bytes;
        private boolean cerrada;

        private EntradaMedida(String nombre, OutputStream destino) {
            this.nombre = nombre;
            this.destino = destino;
            try {
                this.digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 no está disponible", e);
            }
        }

        @Override
        public void write(int valor) throws IOException {
            destino.write(valor);
            digest.update((byte) valor);
            bytes++;
        }

        @Override
        public void write(byte[] contenido, int desde, int cantidad) throws IOException {
            destino.write(contenido, desde, cantidad);
            digest.update(contenido, desde, cantidad);
            bytes += cantidad;
        }

        @Override
        public void flush() throws IOException {
            destino.flush();
        }

        @Override
        public void close() throws IOException {
            if (cerrada) {
                return;
            }
            cerrada = true;
            destino.flush();
            archivos.add(new Archivo(nombre, HexFormat.of().formatHex(digest.digest()), bytes));
        }
    }
}
