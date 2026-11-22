package ar.sistemaodontologico.auth.infrastructure.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import ar.sistemaodontologico.compartido.IntegracionPostgresTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/** Spec seguridad-http — Requirements: Cabeceras de seguridad y CORS restringido al frontend (tareas 6.1 y 6.3). */
class CabecerasDeSeguridadTest extends IntegracionPostgresTest {

    @Autowired
    private TestRestTemplate http;

    @Test
    void conElPerfilPorDefectoMandaLasCuatroCabeceras() {
        for (String ruta : new String[] {"/actuator/health", "/api/v1/pruebas/privado"}) {
            HttpHeaders cabeceras = http.getForEntity(ruta, String.class).getHeaders();

            assertThat(cabeceras.getFirst("Content-Security-Policy")).as(ruta).isEqualTo("default-src 'self'");
            assertThat(cabeceras.getFirst("X-Content-Type-Options")).as(ruta).isEqualTo("nosniff");
            assertThat(cabeceras.getFirst("Referrer-Policy")).as(ruta).isEqualTo("no-referrer");
            assertThat(cabeceras.getFirst("Strict-Transport-Security")).as(ruta)
                    .startsWith("max-age=31536000").contains("includeSubDomains");
        }
    }

    @Test
    void preflightDesdeElFrontendPropioSePermiteConCredenciales() {
        ResponseEntity<String> respuesta = preflight(ORIGEN_FRONTEND);

        assertThat(respuesta.getHeaders().getAccessControlAllowOrigin()).isEqualTo(ORIGEN_FRONTEND);
        assertThat(respuesta.getHeaders().getAccessControlAllowCredentials()).isTrue();
    }

    @Test
    void preflightDesdeOtroOrigenNoRecibePermiso() {
        ResponseEntity<String> respuesta = preflight("https://otro.example.com");

        assertThat(respuesta.getHeaders().getAccessControlAllowOrigin()).isNull();
    }

    // C-19: el frontend lee el nombre del archivo y la espera del 429. Solo se exponen estas dos cabeceras.
    @Test
    void exponeAlFrontendSoloLasCabecerasDeLaDescarga() {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setOrigin(ORIGEN_FRONTEND);

        ResponseEntity<String> respuesta =
                http.exchange("/actuator/health", HttpMethod.GET, new HttpEntity<>(cabeceras), String.class);

        assertThat(respuesta.getHeaders().getAccessControlExposeHeaders())
                .containsExactlyInAnyOrder(HttpHeaders.CONTENT_DISPOSITION, HttpHeaders.RETRY_AFTER);
    }

    private ResponseEntity<String> preflight(String origen) {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setOrigin(origen);
        cabeceras.setAccessControlRequestMethod(HttpMethod.POST);
        cabeceras.add(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type");
        return http.exchange("/api/v1/auth/login", HttpMethod.OPTIONS, new HttpEntity<>(cabeceras), String.class);
    }

    @Nested
    @ActiveProfiles({"test", "local"})
    class ConPerfilLocal extends IntegracionPostgresTest {

        @Autowired
        private TestRestTemplate httpLocal;

        @Test
        void sinHsts() {
            HttpHeaders cabeceras = httpLocal.getForEntity("/actuator/health", String.class).getHeaders();

            assertThat(cabeceras.getFirst("Strict-Transport-Security")).isNull();
            assertThat(cabeceras.getFirst("Content-Security-Policy")).isEqualTo("default-src 'self'");
        }

        // Tarea 6.2: Swagger UI usa estilos inline e íconos data:, así que solo esa ruta relaja la CSP, y solo en local.
        @Test
        void swaggerUiTieneUnaCspPropiaYElRestoSigueEstricto() {
            String swagger = httpLocal.getForEntity("/swagger-ui/index.html", String.class).getHeaders()
                    .getFirst("Content-Security-Policy");
            String api = httpLocal.getForEntity("/api/v1/pruebas/privado", String.class).getHeaders()
                    .getFirst("Content-Security-Policy");

            assertThat(swagger).contains("default-src 'self'", "style-src 'self' 'unsafe-inline'", "img-src 'self' data:")
                    .doesNotContain("script-src");
            assertThat(api).isEqualTo("default-src 'self'");
        }
    }
}
