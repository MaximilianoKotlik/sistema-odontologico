package pruebas.seguridad;

import java.util.Map;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;

/**
 * Login fallido con un {@code X-Forwarded-For} dado, por el servidor real (C-23, design D5). Las conexiones del test
 * llegan desde 127.0.0.1, que con {@code native} es un proxy de confianza (red interna), como el reverse proxy del
 * VPS en el mismo host.
 */
public final class LoginDesdeProxy {

    private LoginDesdeProxy() {
    }

    public static HttpStatusCode login(TestRestTemplate http, String origen, String xForwardedFor) {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setContentType(MediaType.APPLICATION_JSON);
        cabeceras.setOrigin(origen);
        cabeceras.set("X-Forwarded-For", xForwardedFor);
        Map<String, String> cuerpo = Map.of("email", "nadie@example.com", "password", "una-clave-equivocada");
        return http.postForEntity("/api/v1/auth/login", new HttpEntity<>(cuerpo, cabeceras), String.class)
                .getStatusCode();
    }
}
