package ar.sistemaodontologico.plan.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.sistemaodontologico.plan.domain.CatalogoCrudo.PlanCrudo;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Spec public-plans — Requirements: Importes exactos en ARS; Fecha de última actualización; Catálogo público sin
 * autenticación (fuente inválida). Tarea 2.1.
 */
class ArmadorDeCatalogoTest {

    private static PlanCrudo planValido() {
        return new PlanCrudo("consultorio", "Plan consultorio", "Un odontólogo · un consultorio.",
                List.of("Agenda diaria y semanal"), "14900.00", "ARS", true);
    }

    private static CatalogoCrudo catalogo(List<PlanCrudo> planes) {
        return new CatalogoCrudo(planes, 30, false, "2026-11-20T03:00:00Z");
    }

    @Test
    void armaElCatalogoConImporteExactoYFechaUtc() {
        CatalogoDePlanes resultado = ArmadorDeCatalogo.armar(catalogo(List.of(planValido())));

        assertThat(resultado.planes()).singleElement().satisfies(plan -> {
            assertThat(plan.id()).isEqualTo("consultorio");
            assertThat(plan.nombre()).isEqualTo("Plan consultorio");
            assertThat(plan.descripcion()).isEqualTo("Un odontólogo · un consultorio.");
            assertThat(plan.incluye()).containsExactly("Agenda diaria y semanal");
            assertThat(plan.importeArs()).isEqualByComparingTo(new BigDecimal("14900.00"));
            assertThat(plan.importeArs().scale()).isEqualTo(2);
        });
        assertThat(resultado.prueba()).isEqualTo(new Prueba(30, false));
        assertThat(resultado.actualizadoEn()).isEqualTo(Instant.parse("2026-11-20T03:00:00Z"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"PENDIENTE", "14900", "14900.5", "14.900,00", "-1.00", "0.00", " 14900.00"})
    void unPlanSinImporteValidoNoSePublica(String importe) {
        PlanCrudo sinImporte = new PlanCrudo("otro", "Otro plan", null, List.of("Algo"), importe, "ARS", true);

        CatalogoDePlanes resultado = ArmadorDeCatalogo.armar(catalogo(List.of(planValido(), sinImporte)));

        assertThat(resultado.planes()).extracting(Plan::id).containsExactly("consultorio");
    }

    @Test
    void sinNingunPlanPublicableElCatalogoEsInvalido() {
        PlanCrudo pendiente = new PlanCrudo("consultorio", "Plan consultorio", null, List.of("Agenda"), "PENDIENTE",
                "ARS", true);

        assertThatThrownBy(() -> ArmadorDeCatalogo.armar(catalogo(List.of(pendiente))))
                .isInstanceOf(CatalogoInvalido.class);
    }

    @Test
    void sinPlanesElCatalogoEsInvalido() {
        assertThatThrownBy(() -> ArmadorDeCatalogo.armar(catalogo(List.of()))).isInstanceOf(CatalogoInvalido.class);
        assertThatThrownBy(() -> ArmadorDeCatalogo.armar(catalogo(null))).isInstanceOf(CatalogoInvalido.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"2026-11-20", "20/11/2026", "2026-11-20T00:00:00"})
    void unaFechaQueNoEsUnInstanteUtcInvalidaElCatalogo(String fecha) {
        CatalogoCrudo crudo = new CatalogoCrudo(List.of(planValido()), 30, false, fecha);

        assertThatThrownBy(() -> ArmadorDeCatalogo.armar(crudo)).isInstanceOf(CatalogoInvalido.class);
    }

    @Test
    void laPruebaTieneQueSerGratuitaYConDias() {
        assertThatThrownBy(() -> ArmadorDeCatalogo.armar(new CatalogoCrudo(List.of(planValido()), 30, true,
                "2026-11-20T03:00:00Z"))).isInstanceOf(CatalogoInvalido.class);
        assertThatThrownBy(() -> ArmadorDeCatalogo.armar(new CatalogoCrudo(List.of(planValido()), null, false,
                "2026-11-20T03:00:00Z"))).isInstanceOf(CatalogoInvalido.class);
        assertThatThrownBy(() -> ArmadorDeCatalogo.armar(new CatalogoCrudo(List.of(planValido()), 0, false,
                "2026-11-20T03:00:00Z"))).isInstanceOf(CatalogoInvalido.class);
        assertThatThrownBy(() -> ArmadorDeCatalogo.armar(new CatalogoCrudo(List.of(planValido()), 30, null,
                "2026-11-20T03:00:00Z"))).isInstanceOf(CatalogoInvalido.class);
    }

    @Test
    void unPlanMalFormadoInvalidaTodoElCatalogoSinPublicarlosParcialmente() {
        List<PlanCrudo> malos = List.of(
                new PlanCrudo(" ", "Plan", null, List.of("Agenda"), "14900.00", "ARS", true),
                new PlanCrudo("x", null, null, List.of("Agenda"), "14900.00", "ARS", true),
                new PlanCrudo("x", "Plan", null, List.of(), "14900.00", "ARS", true),
                new PlanCrudo("x", "Plan", null, null, "14900.00", "ARS", true),
                new PlanCrudo("x", "Plan", null, List.of(" "), "14900.00", "ARS", true),
                new PlanCrudo("x", "Plan", null, List.of("Agenda"), "14900.00", "USD", true),
                new PlanCrudo("x", "Plan", null, List.of("Agenda"), "14900.00", "ARS", false),
                new PlanCrudo("x", "Plan", null, List.of("Agenda"), "14900.00", "ARS", null));

        for (PlanCrudo malo : malos) {
            assertThatThrownBy(() -> ArmadorDeCatalogo.armar(catalogo(List.of(planValido(), malo))))
                    .as(malo.toString())
                    .isInstanceOf(CatalogoInvalido.class);
        }
    }

    @Test
    void idsRepetidosInvalidanElCatalogo() {
        assertThatThrownBy(() -> ArmadorDeCatalogo.armar(catalogo(List.of(planValido(), planValido()))))
                .isInstanceOf(CatalogoInvalido.class);
    }

    @Test
    void elCatalogoArmadoEsInmutable() {
        List<String> incluye = new ArrayList<>(List.of("Agenda"));
        PlanCrudo plan = new PlanCrudo("consultorio", "Plan consultorio", null, incluye, "14900.00", "ARS", true);

        CatalogoDePlanes resultado = ArmadorDeCatalogo.armar(catalogo(List.of(plan)));
        incluye.add("Otra cosa");

        assertThat(resultado.planes().get(0).incluye()).containsExactly("Agenda");
        assertThatThrownBy(() -> resultado.planes().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
