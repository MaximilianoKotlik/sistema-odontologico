package ar.sistemaodontologico.plan.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.sistemaodontologico.plan.domain.ArmadorDeCatalogo;
import ar.sistemaodontologico.plan.domain.CatalogoCrudo;
import ar.sistemaodontologico.plan.domain.CatalogoDePlanes;
import ar.sistemaodontologico.plan.domain.CatalogoInvalido;
import ar.sistemaodontologico.plan.domain.Prueba;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

/** Spec public-plans — fuente versionada del catálogo (design D1, D2). Tareas 2.1 y 2.3. */
class FuenteDePlanesYamlTest {

    private static FuenteDePlanesYaml desde(String yaml) {
        return new FuenteDePlanesYaml(new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void elCatalogoVersionadoDelRepoEsValidoYTieneElPrecioDeDd22() {
        CatalogoDePlanes catalogo = ArmadorDeCatalogo.armar(
                new FuenteDePlanesYaml(new ClassPathResource(FuenteDePlanesYaml.RECURSO_POR_DEFECTO)).leer());

        assertThat(catalogo.planes()).singleElement().satisfies(plan -> {
            assertThat(plan.nombre()).isEqualTo("Plan consultorio");
            assertThat(plan.importeArs()).isEqualTo(new BigDecimal("14900.00"));
            assertThat(plan.incluye()).isNotEmpty();
        });
        assertThat(catalogo.prueba()).isEqualTo(new Prueba(30, false));
        // Medianoche del 20/11/2026 en Buenos Aires (UTC-3): la página muestra "20 de noviembre de 2026".
        assertThat(catalogo.actualizadoEn()).isEqualTo(Instant.parse("2026-11-20T03:00:00Z"));
    }

    @Test
    void leeElCatalogoCrudoConLosImportesComoTexto() {
        CatalogoCrudo crudo = desde("""
                actualizadoEn: "2026-11-20T03:00:00Z"
                prueba:
                  dias: 30
                  requiereMedioDePago: false
                planes:
                  - id: consultorio
                    nombre: Plan consultorio
                    descripcion: Un odontólogo · un consultorio.
                    incluye:
                      - Agenda diaria y semanal
                    importeARS: "14900.00"
                    moneda: ARS
                    impuestosIncluidos: true
                """).leer();

        assertThat(crudo.actualizadoEn()).isEqualTo("2026-11-20T03:00:00Z");
        assertThat(crudo.diasDePrueba()).isEqualTo(30);
        assertThat(crudo.pruebaRequiereMedioDePago()).isFalse();
        assertThat(crudo.planes()).singleElement().satisfies(plan -> {
            assertThat(plan.id()).isEqualTo("consultorio");
            assertThat(plan.descripcion()).isEqualTo("Un odontólogo · un consultorio.");
            assertThat(plan.incluye()).containsExactly("Agenda diaria y semanal");
            assertThat(plan.importeArs()).isEqualTo("14900.00");
            assertThat(plan.moneda()).isEqualTo("ARS");
            assertThat(plan.impuestosIncluidos()).isTrue();
        });
    }

    @Test
    void unImporteSinComillasNoSeLeeComoPrecioPorqueYamlLoConvierteEnFloat() {
        CatalogoCrudo crudo = desde("""
                actualizadoEn: "2026-11-20T03:00:00Z"
                prueba: {dias: 30, requiereMedioDePago: false}
                planes:
                  - {id: consultorio, nombre: Plan, incluye: [Agenda], importeARS: 14900.00, moneda: ARS, impuestosIncluidos: true}
                """).leer();

        assertThat(crudo.planes().get(0).importeArs()).isNull();
    }

    @Test
    void unaFechaSinComillasNoSeAceptaPorqueYamlLaConvierteEnDate() {
        CatalogoCrudo crudo = desde("""
                actualizadoEn: 2026-11-20T03:00:00Z
                prueba: {dias: 30, requiereMedioDePago: false}
                planes: []
                """).leer();

        assertThat(crudo.actualizadoEn()).isNull();
    }

    @Test
    void unRecursoInexistenteEsUnCatalogoInvalido() {
        FuenteDePlanesYaml fuente = new FuenteDePlanesYaml(new ClassPathResource("no-existe/planes.yaml"));

        assertThatThrownBy(fuente::leer).isInstanceOf(CatalogoInvalido.class);
    }

    @Test
    void unYamlMalFormadoOConTiposEquivocadosEsUnCatalogoInvalido() {
        String[] malos = {
            "planes: [",
            "- solo una lista",
            "",
            "planes: no-es-una-lista",
            "planes:\n  - incluye: no-es-una-lista",
            "planes:\n  - 42",
            "prueba: 30",
        };
        for (String yaml : malos) {
            assertThatThrownBy(() -> desde(yaml).leer()).as(yaml).isInstanceOf(CatalogoInvalido.class);
        }
    }
}
