package ar.sistemaodontologico.arquitectura;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Aplica las reglas de arquitectura al código de producción. */
@AnalyzeClasses(packages = ArchitectureTest.RAIZ, importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    static final String RAIZ = "ar.sistemaodontologico";

    @ArchTest
    static final ArchRule dominioSinFramework = ReglasDeArquitectura.dominioSinFramework();

    @ArchTest
    static final ArchRule infraestructuraPrivadaDelModulo = ReglasDeArquitectura.infraestructuraPrivadaDelModulo(RAIZ);

    @ArchTest
    static final ArchRule horaSoloDesdeElClock = ReglasDeArquitectura.horaSoloDesdeElClock();

    @ArchTest
    static final ArchRule auditoriaSoloPorElPuerto = ReglasDeArquitectura.auditoriaSoloPorElPuerto(RAIZ);

    /**
     * Excepciones a "repositorios siempre con tenant" (C-02, design D9). Cada una cita su decisión; agregar
     * otra requiere aprobación en el PR.
     * <ul>
     *   <li>{@code ConsultorioRepositorio}: es la raíz del tenant; se busca por su propio id o por slug.</li>
     * </ul>
     */
    static final java.util.Set<String> REPOSITORIOS_EXCEPTUADOS = java.util.Set.of("ConsultorioRepositorio");

    @ArchTest
    static final ArchRule repositoriosSiempreConTenant =
            ReglasDeArquitectura.repositoriosSiempreConTenant(REPOSITORIOS_EXCEPTUADOS);

    @ArchTest
    static final ArchRule springDataSoloEnInfraestructura = ReglasDeArquitectura.springDataSoloEnInfraestructura();
}
