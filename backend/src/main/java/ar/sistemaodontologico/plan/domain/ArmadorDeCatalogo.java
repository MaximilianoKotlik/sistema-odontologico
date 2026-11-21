package ar.sistemaodontologico.plan.domain;

import ar.sistemaodontologico.plan.domain.CatalogoCrudo.PlanCrudo;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Valida el catálogo crudo y arma el publicable (spec public-plans, design D1–D3).
 *
 * <ul>
 *   <li>Un error de estructura (plan sin id, moneda que no es ARS, prueba que pide medio de pago, fecha que no es un
 *       instante UTC…) invalida todo el catálogo: nunca se publica una parte.</li>
 *   <li>Un plan sin importe válido (p. ej. el marcador {@code PENDIENTE}) no se publica, pero el resto sí.</li>
 *   <li>Si no queda ningún plan publicable, el catálogo es inválido.</li>
 * </ul>
 */
public final class ArmadorDeCatalogo {

    public static final String MONEDA = "ARS";

    /** Texto decimal con dos decimales (design D2); el importe tiene que ser mayor que cero. */
    private static final Pattern IMPORTE = Pattern.compile("^\\d+\\.\\d{2}$");
    /** Instante UTC con sufijo {@code Z} (spec: fecha de actualización como instante UTC). */
    private static final Pattern INSTANTE_UTC = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(:\\d{2}(\\.\\d+)?)?Z$");

    private ArmadorDeCatalogo() {
    }

    public static CatalogoDePlanes armar(CatalogoCrudo crudo) {
        Instant actualizadoEn = instante(crudo.actualizadoEn());
        Prueba prueba = prueba(crudo.diasDePrueba(), crudo.pruebaRequiereMedioDePago());
        if (crudo.planes() == null || crudo.planes().isEmpty()) {
            throw new CatalogoInvalido("El catálogo no tiene planes");
        }

        Set<String> ids = new HashSet<>();
        List<Plan> publicables = new ArrayList<>();
        for (PlanCrudo plan : crudo.planes()) {
            validarEstructura(plan);
            if (!ids.add(plan.id())) {
                throw new CatalogoInvalido("Hay planes con el mismo id");
            }
            importe(plan.importeArs()).ifPresent(importe -> publicables.add(new Plan(plan.id(), plan.nombre(),
                    plan.descripcion(), List.copyOf(plan.incluye()), importe)));
        }
        if (publicables.isEmpty()) {
            throw new CatalogoInvalido("Ningún plan tiene un importe publicable");
        }
        return new CatalogoDePlanes(List.copyOf(publicables), prueba, actualizadoEn);
    }

    private static Instant instante(String texto) {
        if (texto == null || !INSTANTE_UTC.matcher(texto).matches()) {
            throw new CatalogoInvalido("La fecha de actualización no es un instante UTC");
        }
        try {
            return Instant.parse(texto);
        } catch (DateTimeParseException error) {
            throw new CatalogoInvalido("La fecha de actualización no es un instante UTC", error);
        }
    }

    /** RN-PL-02: la prueba dura un número positivo de días y no pide ningún medio de pago. */
    private static Prueba prueba(Integer dias, Boolean requiereMedioDePago) {
        if (dias == null || dias <= 0 || !Boolean.FALSE.equals(requiereMedioDePago)) {
            throw new CatalogoInvalido("La prueba gratuita está mal definida");
        }
        return new Prueba(dias, false);
    }

    private static void validarEstructura(PlanCrudo plan) {
        boolean valido = plan != null
                && conTexto(plan.id())
                && conTexto(plan.nombre())
                && plan.incluye() != null
                && !plan.incluye().isEmpty()
                && plan.incluye().stream().allMatch(ArmadorDeCatalogo::conTexto)
                && MONEDA.equals(plan.moneda())
                && Boolean.TRUE.equals(plan.impuestosIncluidos());
        if (!valido) {
            throw new CatalogoInvalido("Hay un plan mal definido");
        }
    }

    private static Optional<BigDecimal> importe(String texto) {
        if (texto == null || !IMPORTE.matcher(texto).matches()) {
            return Optional.empty();
        }
        BigDecimal importe = new BigDecimal(texto);
        return importe.signum() > 0 ? Optional.of(importe) : Optional.empty();
    }

    private static boolean conTexto(String texto) {
        return texto != null && !texto.isBlank();
    }
}
