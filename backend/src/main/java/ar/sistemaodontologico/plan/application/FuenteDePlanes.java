package ar.sistemaodontologico.plan.application;

import ar.sistemaodontologico.plan.domain.CatalogoCrudo;

/** Puerto: de dónde sale el catálogo versionado (design D1). Si no se puede leer, lanza {@code CatalogoInvalido}. */
@FunctionalInterface
public interface FuenteDePlanes {

    CatalogoCrudo leer();
}
