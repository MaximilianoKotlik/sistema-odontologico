package ar.sistemaodontologico.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.sistemaodontologico.auth.application.EnvioDeEmailFallido;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * Tarea 3.8 de C-05: el adaptador SMTP contra un Mailpit real, la misma imagen que el docker compose (design D5).
 * Datos ficticios (regla dura 17).
 */
class EnviadorDeEmailsSmtpTest {

    private static final String REMITENTE = "no-responder@example.com";
    private static final String DESTINATARIO = "odontologa.prueba@example.com";
    private static final String ORIGEN = "https://app.example.com";
    private static final String TOKEN = "q7Zt_9xYv2LmN0pK-4sDfGhJ8wErTyUi1oPa3bCdE5f";

    private static final GenericContainer<?> MAILPIT = new GenericContainer<>("axllent/mailpit:v1.27")
            .withExposedPorts(1025, 8025)
            .waitingFor(Wait.forHttp("/api/v1/info").forPort(8025));

    static {
        MAILPIT.start();
    }

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void vaciarBandeja() throws Exception {
        http.send(HttpRequest.newBuilder(api("/api/v1/messages")).DELETE().build(), HttpResponse.BodyHandlers.discarding());
    }

    @Test
    void enviaElEmailDeRecuperacionDesdeElRemitenteConfigurado() throws Exception {
        enviador(MAILPIT.getMappedPort(1025)).enviarRecuperacion(DESTINATARIO, TOKEN);

        JsonNode mensaje = unicoMensaje();
        assertThat(mensaje.at("/From/Address").asText()).isEqualTo(REMITENTE);
        assertThat(mensaje.at("/To/0/Address").asText()).isEqualTo(DESTINATARIO);
        assertThat(mensaje.at("/Subject").asText()).isEqualTo("Restablecé tu contraseña");
    }

    @Test
    void elCuerpoLlevaElLinkConElTokenEnElFragmentoYLaVigencia() throws Exception {
        enviador(MAILPIT.getMappedPort(1025)).enviarRecuperacion(DESTINATARIO, TOKEN);

        JsonNode mensaje = unicoMensaje();
        String texto = mensaje.at("/Text").asText();
        assertThat(texto)
                .contains(ORIGEN + "/recuperar#token=" + TOKEN)
                .contains("30 minutos")
                .contains("Si no lo pediste, podés ignorar")
                .contains("restablecer la contraseña");
        assertThat(mensaje.at("/HTML").asText()).isEmpty();
    }

    @Test
    void unaFallaDelServidorDeCorreoNoExponeElTokenNiElEmail() {
        int puertoSinServidor = 1;

        assertThatThrownBy(() -> enviador(puertoSinServidor).enviarRecuperacion(DESTINATARIO, TOKEN))
                .isInstanceOf(EnvioDeEmailFallido.class)
                .hasMessageNotContaining(TOKEN)
                .hasMessageNotContaining(DESTINATARIO);
    }

    private EnviadorDeEmailsSmtp enviador(int puerto) {
        JavaMailSenderImpl smtp = new JavaMailSenderImpl();
        smtp.setHost(MAILPIT.getHost());
        smtp.setPort(puerto);
        smtp.getJavaMailProperties().put("mail.smtp.connectiontimeout", "2000");
        return new EnviadorDeEmailsSmtp(smtp, new PropiedadesDeMail(REMITENTE, ORIGEN));
    }

    private JsonNode unicoMensaje() throws IOException, InterruptedException {
        JsonNode lista = json.readTree(get("/api/v1/messages"));
        assertThat(lista.at("/messages_count").asInt()).isEqualTo(1);
        String id = lista.at("/messages/0/ID").asText();
        return json.readTree(get("/api/v1/message/" + id));
    }

    private String get(String ruta) throws IOException, InterruptedException {
        return http.send(HttpRequest.newBuilder(api(ruta)).GET().build(), HttpResponse.BodyHandlers.ofString()).body();
    }

    private static URI api(String ruta) {
        return URI.create("http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(8025) + ruta);
    }
}
