package ar.sistemaodontologico.plan.domain;

import java.util.List;

/**
 * Catálogo tal como sale de la fuente versionada, sin validar (design D1). Los importes llegan como texto para no
 * pasar nunca por un tipo de punto flotante (regla dura 15, design D2).
 */
public record CatalogoCrudo(List<PlanCrudo> planes, Integer diasDePrueba, Boolean pruebaRequiereMedioDePago,
        String actualizadoEn) {

    public record PlanCrudo(String id, String nombre, String descripcion, List<String> incluye, String importeArs,
            String moneda, Boolean impuestosIncluidos) {
    }
}
