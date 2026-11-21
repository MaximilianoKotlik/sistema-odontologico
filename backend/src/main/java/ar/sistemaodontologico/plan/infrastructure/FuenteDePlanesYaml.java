package ar.sistemaodontologico.plan.infrastructure;

import ar.sistemaodontologico.plan.application.FuenteDePlanes;
import ar.sistemaodontologico.plan.domain.CatalogoCrudo;
import ar.sistemaodontologico.plan.domain.CatalogoCrudo.PlanCrudo;
import ar.sistemaodontologico.plan.domain.CatalogoInvalido;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * Lee el catálogo de {@code planes.yaml}, versionado en el repo (design D1): cambiar un precio es un PR que actualiza
 * el importe y {@code actualizadoEn}. Se lee en cada consulta, así un archivo inválido responde
 * {@code PLANES_NO_DISPONIBLES} en vez de impedir el arranque de toda la API.
 *
 * <p>Solo se aceptan textos donde el contrato pide texto: un importe sin comillas llega de YAML como número de punto
 * flotante y se descarta (regla dura 15); una fecha sin comillas llega como {@code Date} y también se descarta.
 */
@Component
public class FuenteDePlanesYaml implements FuenteDePlanes {

    public static final String RECURSO_POR_DEFECTO = "planes.yaml";

    private final Resource recurso;

    public FuenteDePlanesYaml(@Value("${app.planes.recurso:classpath:" + RECURSO_POR_DEFECTO + "}") Resource recurso) {
        this.recurso = recurso;
    }

    @Override
    public CatalogoCrudo leer() {
        Map<?, ?> raiz = mapa(cargar(), "raíz");
        Map<?, ?> prueba = raiz.containsKey("prueba") ? mapa(raiz.get("prueba"), "prueba") : Map.of();
        List<PlanCrudo> planes = raiz.get("planes") == null
                ? null
                : lista(raiz.get("planes"), "planes").stream().map(FuenteDePlanesYaml::plan).toList();
        return new CatalogoCrudo(planes, entero(prueba.get("dias")), booleano(prueba.get("requiereMedioDePago")),
                texto(raiz.get("actualizadoEn")));
    }

    private Object cargar() {
        if (!recurso.exists()) {
            throw new CatalogoInvalido("No se encontró el catálogo de planes");
        }
        try (InputStream entrada = recurso.getInputStream()) {
            return new Yaml(new SafeConstructor(new LoaderOptions())).load(entrada);
        } catch (IOException | YAMLException error) {
            throw new CatalogoInvalido("No se pudo leer el catálogo de planes", error);
        }
    }

    private static PlanCrudo plan(Object valor) {
        Map<?, ?> plan = mapa(valor, "plan");
        List<String> incluye = plan.get("incluye") == null
                ? null
                : lista(plan.get("incluye"), "incluye").stream().map(FuenteDePlanesYaml::texto).toList();
        return new PlanCrudo(texto(plan.get("id")), texto(plan.get("nombre")), texto(plan.get("descripcion")),
                incluye, texto(plan.get("importeARS")), texto(plan.get("moneda")),
                booleano(plan.get("impuestosIncluidos")));
    }

    private static Map<?, ?> mapa(Object valor, String nombre) {
        if (valor instanceof Map<?, ?> mapa) {
            return mapa;
        }
        throw new CatalogoInvalido("El catálogo de planes tiene un formato inválido en " + nombre);
    }

    private static List<?> lista(Object valor, String nombre) {
        if (valor instanceof List<?> lista) {
            return lista;
        }
        throw new CatalogoInvalido("El catálogo de planes tiene un formato inválido en " + nombre);
    }

    private static String texto(Object valor) {
        return valor instanceof String texto ? texto : null;
    }

    private static Integer entero(Object valor) {
        return valor instanceof Integer entero ? entero : null;
    }

    private static Boolean booleano(Object valor) {
        return valor instanceof Boolean booleano ? booleano : null;
    }
}
