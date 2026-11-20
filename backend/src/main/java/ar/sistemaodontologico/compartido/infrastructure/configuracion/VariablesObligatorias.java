package ar.sistemaodontologico.compartido.infrastructure.configuracion;

import java.util.List;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Corta el arranque si falta alguna variable de entorno obligatoria y la nombra en el error.
 * Sin esto, Spring deja el placeholder sin resolver y falla más tarde con un mensaje ambiguo.
 * Corre antes de crear cualquier bean, cuando ya están cargadas todas las fuentes de propiedades.
 */
@Component
public class VariablesObligatorias implements BeanFactoryPostProcessor, EnvironmentAware {

    static final List<String> OBLIGATORIAS =
            List.of("DB_URL", "DB_APP_USER", "DB_APP_PASSWORD", "DB_OWNER_USER", "DB_OWNER_PASSWORD",
                    "JWT_SECRET", "APP_FRONTEND_ORIGIN");

    private Environment entorno;

    @Override
    public void setEnvironment(Environment entorno) {
        this.entorno = entorno;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory fabrica) {
        List<String> faltantes = OBLIGATORIAS.stream()
                .filter(variable -> !StringUtils.hasText(entorno.getProperty(variable)))
                .toList();
        if (!faltantes.isEmpty()) {
            throw new IllegalStateException(
                    "Faltan variables de entorno obligatorias: " + String.join(", ", faltantes)
                            + ". Copiá .env.example a .env y completalas.");
        }
    }
}
