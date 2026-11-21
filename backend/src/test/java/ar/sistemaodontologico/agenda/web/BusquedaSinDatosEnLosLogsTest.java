package ar.sistemaodontologico.agenda.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;

/**
 * El texto buscado no queda en los logs (C-12, tarea 4.2; spec busqueda-de-pacientes, design D7; RN-GL-01). Pide por
 * HTTP real al Tomcat embebido, con {@code org.springframework.web} en DEBUG: ahí el {@code DispatcherServlet} escribe
 * la URL de cada petición con su query, que es lo más parecido a un log de acceso que tiene hoy el backend.
 */
@ExtendWith(OutputCaptureExtension.class)
class BusquedaSinDatosEnLosLogsTest extends ApiDeAgendaTest {

    private static final String LOGGER_WEB = "org.springframework.web";

    @Autowired
    private LoggingSystem logging;

    @LocalServerPort
    private int puerto;

    @BeforeEach
    void logsDetallados() {
        logging.setLogLevel(LOGGER_WEB, LogLevel.DEBUG);
    }

    @AfterEach
    void logsNormales() {
        logging.setLogLevel(LOGGER_WEB, null);
    }

    @Test
    void dniFueraDelLog(CapturedOutput salida) throws Exception {
        agenda.paciente("Ana", "Prueba", "90000001", "+5491100000001", null);

        assertThat(buscar("90000001")).isEqualTo(200);

        assertThat(salida.getAll()).contains("/api/v1/pacientes?q=***").doesNotContain("90000001");
    }

    @Test
    void nombreFueraDelLog(CapturedOutput salida) throws Exception {
        agenda.paciente("Carla", "Ficticia");

        assertThat(buscar("Ficticia")).isEqualTo(200);

        assertThat(salida.getAll()).contains("/api/v1/pacientes?q=***").doesNotContain("Ficticia");
    }

    @Test
    void lasOtrasRutasNoSeTocan(CapturedOutput salida) throws Exception {
        HttpResponse<String> respuesta = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                        URI.create("http://localhost:" + puerto + "/api/v1/bloqueos?desde=2026-11-23T03:00:00Z"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(salida.getAll()).contains("/api/v1/bloqueos?desde=2026-11-23T03:00:00Z");
    }

    private int buscar(String q) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                        URI.create("http://localhost:" + puerto + "/api/v1/pacientes?q=" + q))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).GET().build(),
                HttpResponse.BodyHandlers.ofString()).statusCode();
    }
}
