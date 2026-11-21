package ar.sistemaodontologico.reserva.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.sistemaodontologico.compartido.application.EnviadorDeEmails;
import ar.sistemaodontologico.compartido.application.EnvioDeEmailFallido;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pruebas.agenda.AgendaDePrueba;

/** Confirmación por email después del commit (C-15, tarea 6.7, DD-26, design D12). */
class EmailDeReservaTest extends ApiPublicaTest {

    @MockitoBean
    private EnviadorDeEmails enviador;

    @Test
    void reservaConEmailEnviaFechaConsultorioYToken() throws Exception {
        Map<String, Object> cuerpo = pedido(lunesA("10:00"), AgendaDePrueba.dniNuevo());
        cuerpo.put("email", "paciente1@example.com");

        String respuesta = reservar(slug, cuerpo).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString();

        String token = json.readTree(respuesta).path("linkGestion").asText().substring(3);
        verify(enviador, timeout(5000)).enviarConfirmacionDeTurno(eq("paciente1@example.com"), eq("Consultorio Demo"),
                contains("10:00"), eq(token));
    }

    @Test
    void sinEmailNoSeEnviaNada() throws Exception {
        reservar(slug, pedido(lunesA("10:00"), AgendaDePrueba.dniNuevo())).andExpect(status().isCreated());

        verify(enviador, after(300).never()).enviarConfirmacionDeTurno(any(), any(), any(), any());
    }

    @Test
    void unaReservaRechazadaNoEnviaNada() throws Exception {
        reservarYObtenerToken(lunesA("10:00"));
        Map<String, Object> cuerpo = pedido(lunesA("10:00"), AgendaDePrueba.dniNuevo());
        cuerpo.put("email", "paciente2@example.com");

        reservar(slug, cuerpo).andExpect(status().isConflict());

        verify(enviador, after(300).never()).enviarConfirmacionDeTurno(eq("paciente2@example.com"), any(), any(), any());
    }

    @Test
    void conSmtpCaidoLaReservaQuedaCreada() throws Exception {
        doThrow(new EnvioDeEmailFallido("SMTP caído")).when(enviador).enviarConfirmacionDeTurno(any(), any(), any(), any());
        Map<String, Object> cuerpo = pedido(lunesA("10:00"), AgendaDePrueba.dniNuevo());
        cuerpo.put("email", "paciente3@example.com");

        reservar(slug, cuerpo).andExpect(status().isCreated());

        verify(enviador, timeout(5000)).enviarConfirmacionDeTurno(eq("paciente3@example.com"), any(), any(), any());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM turno WHERE consultorio_id = ?", Long.class,
                agenda.consultorio())).isEqualTo(1);
        verify(enviador, never()).enviarRecuperacion(any(), any());
    }
}
