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

    private final JavaClasses fixturesDeRepositorios =
            new ClassFileImporter().importPackages("pruebas.arquitectura.repositorios");

    @Test
    void unRepositorioQueConsultaSinConsultorioViolaLaRegla() {
        EvaluationResult resultado = ReglasDeArquitectura.repositoriosSiempreConTenant(java.util.Set.of("ConsultorioRepositorio"))
                .evaluate(fixturesDeRepositorios);

        assertThat(resultado.hasViolation()).isTrue();
        assertThat(resultado.getFailureReport().getDetails())
                .anyMatch(detalle -> detalle.contains("TurnoSinTenantRepositorio.buscarPorId"))
                .anyMatch(detalle -> detalle.contains("TurnoSinTenantRepositorio.listarTodos"))
                .noneMatch(detalle -> detalle.contains("PacienteRepositorio"))
                .noneMatch(detalle -> detalle.contains("ConsultorioRepositorio"));
    }

    @Test
    void sinLaExcepcionElRepositorioDeConsultorioTambienViolaLaRegla() {
        EvaluationResult resultado = ReglasDeArquitectura.repositoriosSiempreConTenant(java.util.Set.of())
                .evaluate(fixturesDeRepositorios);

        assertThat(resultado.getFailureReport().getDetails())
                .anyMatch(detalle -> detalle.contains("ConsultorioRepositorio.buscarPorSlug"));
    }

    @Test
    void usarSpringDataFueraDeLaInfraestructuraViolaLaRegla() {
        EvaluationResult resultado = ReglasDeArquitectura.springDataSoloEnInfraestructura().evaluate(fixturesDeRepositorios);

        assertThat(resultado.hasViolation()).isTrue();
        assertThat(resultado.getFailureReport().getDetails())
                .anyMatch(detalle -> detalle.contains("ServicioQueUsaSpringData"))
                .noneMatch(detalle -> detalle.contains("TurnoRepositorioAdaptador"));
    }

    @Test
    void tomarLaHoraSinElClockInyectadoViolaLaRegla() {
        EvaluationResult resultado = ReglasDeArquitectura.horaSoloDesdeElClock().evaluate(fixtures);

        assertThat(resultado.hasViolation()).isTrue();
        assertThat(resultado.getFailureReport().getDetails())
                .anyMatch(detalle -> detalle.contains("CalculoConNowDirecto") && detalle.contains("Instant.now()"))
                .anyMatch(detalle -> detalle.contains("CalculoConNowDirecto") && detalle.contains("LocalDate.now()"))
                .noneMatch(detalle -> detalle.contains("CalculoConClock"));
    }

    @Test
    void usarElModuloDeAuditoriaDesdeOtroModuloViolaLaRegla() {
        EvaluationResult resultado =
                ReglasDeArquitectura.auditoriaSoloPorElPuerto(RAIZ_FIXTURES).evaluate(fixtures);

        assertThat(resultado.hasViolation()).isTrue();
        assertThat(resultado.getFailureReport().getDetails())
                .anyMatch(detalle -> detalle.contains("ServicioQueAuditaDirecto")
                        && detalle.contains("RegistroDirectoDePrueba"))
                .noneMatch(detalle -> detalle.contains("ValidadorInternoDePrueba"));
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
