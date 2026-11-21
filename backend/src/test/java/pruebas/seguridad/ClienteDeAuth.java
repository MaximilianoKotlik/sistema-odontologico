package pruebas.seguridad;

import java.util.Map;
import java.util.UUID;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Solo de test (C-05): llamadas HTTP a los endpoints de auth con datos ficticios (regla dura 17). Registra cuentas
 * por la API real, así los tests ejercitan el mismo camino que el frontend.
 */
public final class ClienteDeAuth {

    public static final String CONTRASENA = "una-clave-larga";

    private final TestRestTemplate http;
    private final String origen;

    public ClienteDeAuth(TestRestTemplate http, String origen) {
        this.http = http;
        this.origen = origen;
    }

    /** Registra una cuenta nueva y devuelve su email. */
    public String registrar(String prefijo) {
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        String email = prefijo + "-" + sufijo + "@example.com";
        ResponseEntity<String> respuesta = http.postForEntity("/api/v1/auth/registro", json(Map.of(
                "nombre", "Ana", "apellido", "Prueba", "email", email, "password", CONTRASENA,
                "nombreConsultorio", "Consultorio " + prefijo + " " + sufijo, "aceptaTerminos", true)), String.class);
        if (respuesta.getStatusCode() != HttpStatus.CREATED) {
            throw new IllegalStateException("No se pudo registrar la cuenta de prueba: " + respuesta.getStatusCode());
        }
        return email;
    }

    public ResponseEntity<String> login(String email, String password) {
        return http.postForEntity("/api/v1/auth/login", json(Map.of("email", email, "password", password)),
                String.class);
    }

    public ResponseEntity<String> post(String ruta, Object cuerpo) {
        return http.postForEntity(ruta, json(cuerpo), String.class);
    }

    public ResponseEntity<String> postConSesion(String ruta, Object cuerpo, String accessToken, String refresh) {
        HttpHeaders cabeceras = cabecerasJson();
        cabeceras.setBearerAuth(accessToken);
        if (refresh != null) {
            cabeceras.add(HttpHeaders.COOKIE, "refresh=" + refresh);
        }
        return http.exchange(ruta, HttpMethod.POST, new HttpEntity<>(cuerpo, cabeceras), String.class);
    }

    public ResponseEntity<String> refrescar(String refresh) {
        HttpHeaders cabeceras = cabecerasJson();
        cabeceras.add(HttpHeaders.COOKIE, "refresh=" + refresh);
        return http.exchange("/api/v1/auth/refresh", HttpMethod.POST, new HttpEntity<>(null, cabeceras), String.class);
    }

    public ResponseEntity<String> getConSesion(String ruta, String accessToken) {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setBearerAuth(accessToken);
        return http.exchange(ruta, HttpMethod.GET, new HttpEntity<>(null, cabeceras), String.class);
    }

    /** Valor de la cookie de refresh que puso la respuesta, o {@code null}. */
    public static String refreshDe(ResponseEntity<?> respuesta) {
        String cookie = respuesta.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        if (cookie == null || !cookie.startsWith("refresh=")) {
            return null;
        }
        return cookie.substring("refresh=".length(), cookie.indexOf(';'));
    }

    private HttpEntity<Object> json(Object cuerpo) {
        return new HttpEntity<>(cuerpo, cabecerasJson());
    }

    private HttpHeaders cabecerasJson() {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setContentType(MediaType.APPLICATION_JSON);
        cabeceras.setOrigin(origen);
        return cabeceras;
    }
}
