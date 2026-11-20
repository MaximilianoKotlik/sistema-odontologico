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
}
