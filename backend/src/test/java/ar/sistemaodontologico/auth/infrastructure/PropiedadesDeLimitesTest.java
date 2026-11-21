package ar.sistemaodontologico.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.sistemaodontologico.compartido.application.LimiteDeSolicitudes;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/** Design D8: los límites de solicitudes salen de {@code app.rate-limit.*}. */
class PropiedadesDeLimitesTest {

    @Test
    void losValoresPorDefectoSonLosDelDesign() throws IOException {
        PropiedadesDeLimites limites = enlazar(new ClassPathResource("application.yml"));

        assertThat(limites.login()).isEqualTo(limite("login", 10, Duration.ofMinutes(1)));
        assertThat(limites.registro()).isEqualTo(limite("registro", 5, Duration.ofHours(1)));
        assertThat(limites.refresh()).isEqualTo(limite("refresh", 30, Duration.ofMinutes(1)));
        assertThat(limites.recuperacion()).isEqualTo(limite("recuperacion", 5, Duration.ofHours(1)));
        assertThat(limites.recuperacionPorEmail()).isEqualTo(limite("recuperacion-por-email", 3, Duration.ofHours(1)));
        assertThat(limites.confirmarRecuperacion())
                .isEqualTo(limite("confirmar-recuperacion", 10, Duration.ofHours(1)));
        assertThat(limites.cambiarPassword()).isEqualTo(limite("cambiar-password", 5, Duration.ofMinutes(15)));
    }

    @Test
    void cadaLimiteTieneUnNombreDistinto() throws IOException {
        PropiedadesDeLimites limites = enlazar(new ClassPathResource("application.yml"));

        List<String> nombres = List.of(limites.login(), limites.registro(), limites.refresh(), limites.recuperacion(),
                        limites.recuperacionPorEmail(), limites.confirmarRecuperacion(), limites.cambiarPassword())
                .stream().map(LimiteDeSolicitudes::nombre).toList();

        assertThat(nombres).doesNotHaveDuplicates();
    }

    @Test
    void seConfiguraCadaLimitePorSeparado() {
        Map<String, String> valores = Map.of(
                "app.rate-limit.login.capacidad", "3",
                "app.rate-limit.login.periodo", "30s");

        PropiedadesDeLimites limites = new Binder(new MapConfigurationPropertySource(valores))
                .bindOrCreate("app.rate-limit", PropiedadesDeLimites.class);

        assertThat(limites.login()).isEqualTo(limite("login", 3, Duration.ofSeconds(30)));
        assertThat(limites.registro()).isNull();
    }

    @Test
    void rechazaUnaCapacidadQueNoEsPositiva() {
        Map<String, String> valores = Map.of(
                "app.rate-limit.login.capacidad", "0",
                "app.rate-limit.login.periodo", "1m");

        assertThatThrownBy(() -> new Binder(new MapConfigurationPropertySource(valores))
                .bind("app.rate-limit", PropiedadesDeLimites.class))
                .isInstanceOf(BindException.class);
    }

    private static LimiteDeSolicitudes limite(String nombre, long capacidad, Duration periodo) {
        return new LimiteDeSolicitudes(nombre, capacidad, periodo);
    }

    private static PropiedadesDeLimites enlazar(ClassPathResource yaml) throws IOException {
        List<PropertySource<?>> fuentes = new YamlPropertySourceLoader().load("yaml", yaml);
        return Binder.get(new org.springframework.core.env.StandardEnvironment() {
            {
                fuentes.forEach(getPropertySources()::addLast);
            }
        }).bind("app.rate-limit", PropiedadesDeLimites.class).get();
    }
}
