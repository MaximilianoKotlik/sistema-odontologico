package ar.sistemaodontologico.compartido;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.sistemaodontologico.Application;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

/** Spec plataforma/entorno-local — Requirement: Configuración por variables de entorno. */
class ConfiguracionObligatoriaTest {

    private static SpringApplicationBuilder aplicacionCon(String... propiedades) {
        return new SpringApplicationBuilder(Application.class)
                .web(WebApplicationType.NONE)
                .profiles("test")
                .properties(propiedades);
    }

    @Test
    void sinLaUrlDeLaBaseElArranqueFallaNombrandoLaVariable() {
        SpringApplicationBuilder aplicacion = aplicacionCon(
                "DB_APP_USER=app_rw", "DB_APP_PASSWORD=x", "DB_OWNER_USER=app_owner", "DB_OWNER_PASSWORD=x");

        assertThatThrownBy(() -> aplicacion.run().close())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_URL")
                .hasMessageNotContaining("DB_APP_USER");
    }

    @Test
    void nombraTodasLasVariablesQueFaltan() {
        SpringApplicationBuilder aplicacion = aplicacionCon("DB_URL=jdbc:postgresql://localhost/agenda", "DB_APP_USER=app_rw");

        assertThatThrownBy(() -> aplicacion.run().close())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_APP_PASSWORD")
                .hasMessageContaining("DB_OWNER_USER")
                .hasMessageContaining("DB_OWNER_PASSWORD")
                .hasMessageNotContaining("DB_URL");
    }
}
