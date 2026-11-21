package pruebas.reloj;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Reemplaza el {@code Clock} de la aplicación por un {@link RelojDePrueba} que el test puede adelantar. */
@TestConfiguration(proxyBeanMethods = false)
public class ConfiguracionDeRelojDePrueba {

    @Bean
    @Primary
    RelojDePrueba relojDePrueba() {
        return new RelojDePrueba();
    }
}
