package ar.sistemaodontologico.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import ar.sistemaodontologico.auth.application.LimitadorDeSolicitudes;
import ar.sistemaodontologico.compartido.IntegracionPostgresTest;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Tarea 0.3 de C-05: dependencias y configuración de mail y rate limit. En el perfil de tests los límites son bajos,
 * para que los tests de integración los alcancen rápido.
 */
class ConfiguracionDeAuthC05Test extends IntegracionPostgresTest {

    @Autowired
    private PropiedadesDeLimites limites;

    @Autowired
    private LimitadorDeSolicitudes limitador;

    @Autowired
    private JavaMailSender enviador;

    @Autowired
    private TestRestTemplate http;

    @Test
    void elPerfilDeTestsUsaLimitesBajos() {
        assertThat(limites.login().capacidad()).isEqualTo(3);
        assertThat(limites.recuperacionPorEmail().capacidad()).isEqualTo(2);
        assertThat(limites.cambiarPassword().periodo()).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void elLimitadorYElEnviadorDeEmailsEstanDisponibles() {
        assertThat(limitador).isInstanceOf(LimitadorBucket4j.class);
        assertThat(enviador).isNotNull();
    }

    @Test
    void elHealthNoDependeDelServidorDeCorreo() {
        // En tests no hay SMTP: si el health lo incluyera, quedaría DOWN y docker compose reiniciaría el backend
        // ante una caída del correo, que la recuperación tolera (design D4).
        ResponseEntity<String> respuesta = http.getForEntity("/actuator/health", String.class);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        assertThat(respuesta.getBody()).contains("\"UP\"");
    }
}
