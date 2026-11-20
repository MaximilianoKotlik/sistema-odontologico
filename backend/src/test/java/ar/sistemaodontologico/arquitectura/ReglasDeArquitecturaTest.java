package ar.sistemaodontologico.arquitectura;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.EvaluationResult;
import org.junit.jupiter.api.Test;

/** Verifica que las reglas de arquitectura detectan las violaciones (y solo esas). */
class ReglasDeArquitecturaTest {

    private static final String RAIZ_FIXTURES = "ar.sistemaodontologico.arquitectura.fixtures";

    private final JavaClasses fixtures = new ClassFileImporter().importPackages(RAIZ_FIXTURES);

    @Test
    void elDominioQueImportaSpringOJpaViolaLaRegla() {
        EvaluationResult resultado = ReglasDeArquitectura.dominioSinFramework().evaluate(fixtures);

        assertThat(resultado.hasViolation()).isTrue();
        assertThat(resultado.getFailureReport().getDetails())
                .anyMatch(detalle -> detalle.contains("TurnoConSpring"))
                .anyMatch(detalle -> detalle.contains("TurnoConJpa"))
                .noneMatch(detalle -> detalle.contains("TurnoPuro"));
    }

    @Test
    void usarLaInfraestructuraDeOtroModuloViolaLaRegla() {
        EvaluationResult resultado =
                ReglasDeArquitectura.infraestructuraPrivadaDelModulo(RAIZ_FIXTURES).evaluate(fixtures);

        assertThat(resultado.hasViolation()).isTrue();
        assertThat(resultado.getFailureReport().getDetails())
                .anyMatch(detalle -> detalle.contains("ServicioDePagos") && detalle.contains("TurnoRepositorioJpa"))
                .noneMatch(detalle -> detalle.contains("ServicioDeTurnos"));
    }
}
