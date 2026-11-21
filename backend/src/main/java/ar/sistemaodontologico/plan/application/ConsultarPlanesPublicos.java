package ar.sistemaodontologico.plan.application;

import ar.sistemaodontologico.compartido.domain.error.ErrorDeNegocio;
import ar.sistemaodontologico.compartido.domain.error.TipoDeError;
import ar.sistemaodontologico.plan.domain.ArmadorDeCatalogo;
import ar.sistemaodontologico.plan.domain.CatalogoDePlanes;
import ar.sistemaodontologico.plan.domain.CatalogoInvalido;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Catálogo público de planes (US-060, RN-PL-01). No depende de un tenant: es el mismo para cualquier visitante y no
 * toca la base. Si la fuente falta o es inválida responde {@code PLANES_NO_DISPONIBLES}, sin importes parciales.
 */
@Service
public class ConsultarPlanesPublicos {

    private static final Logger LOG = LoggerFactory.getLogger(ConsultarPlanesPublicos.class);

    private final FuenteDePlanes fuente;

    public ConsultarPlanesPublicos(FuenteDePlanes fuente) {
        this.fuente = fuente;
    }

    public CatalogoDePlanes consultar() {
        try {
            return ArmadorDeCatalogo.armar(fuente.leer());
        } catch (CatalogoInvalido error) {
            // El motivo es de configuración (nunca datos personales): sirve para corregir planes.yaml.
            LOG.error("Catálogo de planes no disponible: {}", error.getMessage());
            throw new PlanesNoDisponibles();
        }
    }

    /** 503 en Problem Details: el catálogo no se puede publicar ahora. */
    static final class PlanesNoDisponibles extends ErrorDeNegocio {

        PlanesNoDisponibles() {
            super("PLANES_NO_DISPONIBLES", TipoDeError.NO_DISPONIBLE, "Los precios no están disponibles en este momento");
        }
    }
}
