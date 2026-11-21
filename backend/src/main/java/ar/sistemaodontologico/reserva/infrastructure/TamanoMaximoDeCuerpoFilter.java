package ar.sistemaodontologico.reserva.infrastructure;

import ar.sistemaodontologico.compartido.web.ManejadorGlobalDeErrores;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Cuerpo de hasta 64 KB en {@code /api/v1/public/**} (design D9). Con {@code Content-Length} rechaza sin leer; sin él
 * (chunked) lee como máximo un byte más que el límite y entrega a los controladores una copia en memoria.
 */
class TamanoMaximoDeCuerpoFilter extends OncePerRequestFilter {

    static final int MAXIMO_BYTES = 64 * 1024;

    private final ObjectMapper json;

    TamanoMaximoDeCuerpoFilter(ObjectMapper json) {
        this.json = json;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        if (HttpMethod.GET.matches(peticion.getMethod()) || HttpMethod.HEAD.matches(peticion.getMethod())) {
            cadena.doFilter(peticion, respuesta);
            return;
        }
        long declarado = peticion.getContentLengthLong();
        if (declarado > MAXIMO_BYTES) {
            rechazar(peticion, respuesta);
            return;
        }
        if (declarado >= 0) {
            cadena.doFilter(peticion, respuesta);
            return;
        }
        byte[] cuerpo = peticion.getInputStream().readNBytes(MAXIMO_BYTES + 1);
        if (cuerpo.length > MAXIMO_BYTES) {
            rechazar(peticion, respuesta);
            return;
        }
        cadena.doFilter(new PeticionConCuerpo(peticion, cuerpo), respuesta);
    }

    private void rechazar(HttpServletRequest peticion, HttpServletResponse respuesta) throws IOException {
        respuesta.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
        respuesta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        json.writeValue(respuesta.getOutputStream(), ManejadorGlobalDeErrores.problemaPara(HttpStatus.PAYLOAD_TOO_LARGE,
                "CUERPO_DEMASIADO_GRANDE", "El cuerpo de la petición es demasiado grande", peticion.getRequestURI()));
    }

    /** La petición con el cuerpo ya leído, que se puede volver a leer desde memoria. */
    private static final class PeticionConCuerpo extends HttpServletRequestWrapper {

        private final byte[] cuerpo;

        PeticionConCuerpo(HttpServletRequest peticion, byte[] cuerpo) {
            super(peticion);
            this.cuerpo = cuerpo;
        }

        @Override
        public int getContentLength() {
            return cuerpo.length;
        }

        @Override
        public long getContentLengthLong() {
            return cuerpo.length;
        }

        @Override
        public BufferedReader getReader() {
            String codificacion = getCharacterEncoding();
            Charset charset = codificacion == null ? StandardCharsets.UTF_8 : Charset.forName(codificacion);
            return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(cuerpo), charset));
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream entrada = new ByteArrayInputStream(cuerpo);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return entrada.read();
                }

                @Override
                public int read(byte[] destino, int desde, int largo) {
                    return entrada.read(destino, desde, largo);
                }

                @Override
                public boolean isFinished() {
                    return entrada.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException("Lectura asíncrona no soportada");
                }
            };
        }
    }
}
