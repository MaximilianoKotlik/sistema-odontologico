package ar.sistemaodontologico.reserva.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Cuerpo de hasta 64 KB, también si llega chunked y sin Content-Length (C-15, tarea 8.2, design D9). */
class TamanoMaximoDeCuerpoFilterTest {

    private static final int MAXIMO = 64 * 1024;

    private final TamanoMaximoDeCuerpoFilter filtro = new TamanoMaximoDeCuerpoFilter(new ObjectMapper());

    @Test
    void contentLengthMayorSeRechazaSinLeer() throws Exception {
        MockHttpServletRequest peticion = peticion(new byte[MAXIMO + 1], false);
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        AtomicReference<ServletRequest> siguiente = new AtomicReference<>();

        filtro.doFilter(peticion, respuesta, (req, res) -> siguiente.set(req));

        assertThat(respuesta.getStatus()).isEqualTo(413);
        assertThat(respuesta.getContentAsString()).contains("CUERPO_DEMASIADO_GRANDE");
        assertThat(siguiente).hasValue(null);
    }

    @Test
    void chunkedMayorSeRechaza() throws Exception {
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        AtomicReference<ServletRequest> siguiente = new AtomicReference<>();

        filtro.doFilter(peticion(new byte[MAXIMO + 1], true), respuesta, (req, res) -> siguiente.set(req));

        assertThat(respuesta.getStatus()).isEqualTo(413);
        assertThat(siguiente).hasValue(null);
    }

    @Test
    void chunkedEnElLimitePasaConElCuerpoIntacto() throws Exception {
        byte[] cuerpo = "a".repeat(MAXIMO).getBytes(StandardCharsets.UTF_8);
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        AtomicReference<byte[]> leido = new AtomicReference<>();

        filtro.doFilter(peticion(cuerpo, true), respuesta,
                (req, res) -> leido.set(((HttpServletRequest) req).getInputStream().readAllBytes()));

        assertThat(respuesta.getStatus()).isEqualTo(200);
        assertThat(leido.get()).isEqualTo(cuerpo);
    }

    @Test
    void unGetPasaSinLeerElCuerpo() throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/api/v1/public/consultorios/demo");
        AtomicReference<ServletRequest> siguiente = new AtomicReference<>();

        filtro.doFilter(peticion, new MockHttpServletResponse(), (req, res) -> siguiente.set(req));

        assertThat(siguiente.get()).isSameAs(peticion);
    }

    private static MockHttpServletRequest peticion(byte[] cuerpo, boolean chunked) {
        MockHttpServletRequest peticion = new MockHttpServletRequest("POST", "/api/v1/public/consultorios/demo/turnos") {
            @Override
            public long getContentLengthLong() {
                return chunked ? -1 : super.getContentLengthLong();
            }

            @Override
            public int getContentLength() {
                return chunked ? -1 : super.getContentLength();
            }
        };
        peticion.setContent(cuerpo);
        return peticion;
    }
}
