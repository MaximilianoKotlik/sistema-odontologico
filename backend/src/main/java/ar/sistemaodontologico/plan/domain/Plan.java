package ar.sistemaodontologico.plan.domain;

import java.math.BigDecimal;
import java.util.List;

/** Plan publicable: importe exacto en ARS con impuestos incluidos (RN-PL-01, regla dura 15). */
public record Plan(String id, String nombre, String descripcion, List<String> incluye, BigDecimal importeArs) {
}
