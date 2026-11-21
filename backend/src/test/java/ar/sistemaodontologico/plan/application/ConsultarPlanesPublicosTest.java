package ar.sistemaodontologico.plan.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.sistemaodontologico.compartido.domain.error.ErrorDeNegocio;
import ar.sistemaodontologico.compartido.domain.error.TipoDeError;
import ar.sistemaodontologico.plan.domain.CatalogoCrudo;
import ar.sistemaodontologico.plan.domain.CatalogoCrudo.PlanCrudo;
import ar.sistemaodontologico.plan.domain.CatalogoDePlanes;
import ar.sistemaodontologico.plan.domain.CatalogoInvalido;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Spec public-plans — Requirement: Catálogo público sin autenticación (fuente no disponible). Tareas 2.1 y 2.3. */
class ConsultarPlanesPublicosTest {

    private static final CatalogoCrudo VALIDO = new CatalogoCrudo(
            List.of(new PlanCrudo("consultorio", "Plan consultorio", null, List.of("Agenda"), "14900.00", "ARS", true)),
            30, false, "2026-11-20T03:00:00Z");

    @Test
    void devuelveElCatalogoValidadoDeLaFuente() {
        CatalogoDePlanes catalogo = new ConsultarPlanesPublicos(() -> VALIDO).consultar();

        assertThat(catalogo.planes()).hasSize(1);
    }

    @Test
    void unaFuenteInvalidaRespondePlanesNoDisponibles() {
        CatalogoCrudo sinFecha = new CatalogoCrudo(VALIDO.planes(), 30, false, null);

        assertThatThrownBy(() -> new ConsultarPlanesPublicos(() -> sinFecha).consultar())
                .isInstanceOfSatisfying(ErrorDeNegocio.class, error -> {
                    assertThat(error.getCodigo()).isEqualTo("PLANES_NO_DISPONIBLES");
                    assertThat(error.getTipo()).isEqualTo(TipoDeError.NO_DISPONIBLE);
                });
    }

    @Test
    void unaFuenteQueNoSePuedeLeerRespondePlanesNoDisponibles() {
        FuenteDePlanes rota = () -> {
            throw new CatalogoInvalido("no se encontró planes.yaml");
        };

        assertThatThrownBy(() -> new ConsultarPlanesPublicos(rota).consultar())
                .isInstanceOfSatisfying(ErrorDeNegocio.class,
                        error -> assertThat(error.getCodigo()).isEqualTo("PLANES_NO_DISPONIBLES"));
    }
}
