package ar.sistemaodontologico.plan.domain;

import java.time.Instant;
import java.util.List;

/** Catálogo público validado: solo planes publicables y la fecha de actualización como instante UTC (RN-PL-01). */
public record CatalogoDePlanes(List<Plan> planes, Prueba prueba, Instant actualizadoEn) {
}
