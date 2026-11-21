package ar.sistemaodontologico.reserva.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.sistemaodontologico.compartido.domain.EnmascaradorDePii;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.TestPropertySource;

/** El token de gestión no llega a los logs ni a los Problem Details (C-15, tarea 8.3, design D10). */
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = {"logging.level.org.springframework.web=DEBUG",
    "logging.level.ar.sistemaodontologico=DEBUG"})
class TokenFueraDeLogsTest extends ApiPublicaTest {

    private static final String TOKEN_INEXISTENTE = "DDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD";

    @Test
    void niLaVistaNiLaCancelacionNiEl404LoguéanElToken(CapturedOutput salida) throws Exception {
        String token = reservarYObtenerToken(lunesA("10:00"));

        ver(token).andExpect(status().isOk());
        cancelar(token).andExpect(status().isOk());
        ver(TOKEN_INEXISTENTE).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.instance").value("/api/v1/public/turnos/***"));
        LoggerFactory.getLogger(TokenFueraDeLogsTest.class).warn("GET /api/v1/public/turnos/{}/huecos", token);

        assertThat(salida.getAll()).doesNotContain(token, TOKEN_INEXISTENTE).contains("/api/v1/public/turnos/***");
    }

    @Test
    void elEnmascaradorCubreUnTokenCortoEnLaRuta() {
        assertThat(EnmascaradorDePii.enmascarar("POST /api/v1/public/turnos/corto/cancelacion"))
                .isEqualTo("POST /api/v1/public/turnos/***/cancelacion");
    }
}
