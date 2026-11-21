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
     *   <li>{@code UsuarioRepositorio.buscarPorEmail}: el login todavía no tiene tenant y el email es único en todo
     *       el sistema (RN-TE-02; C-04, design D9).</li>
     *   <li>{@code UsuarioRepositorio.buscarPorEmailParaActualizar}: la misma búsqueda del login, con lock de fila para
     *       contar los fallos sin perder incrementos (C-05, design D1).</li>
     *   <li>{@code RefreshTokenRepositorio.buscarPorHashParaActualizar}: el refresh llega sin access token; el hash de 256 bits
     *       es la credencial misma (C-04, design D9).</li>
     *   <li>{@code TokenDeRecuperacionRepositorio.buscarPorHashParaActualizar}: la confirmación de la recuperación llega
     *       sin sesión; el hash de 256 bits es la credencial misma (C-05, design D10).</li>
     * </ul>
     */
    static final java.util.Set<String> REPOSITORIOS_EXCEPTUADOS = java.util.Set.of(
            "ConsultorioRepositorio",
            "UsuarioRepositorio.buscarPorEmail",
            "UsuarioRepositorio.buscarPorEmailParaActualizar",
            "RefreshTokenRepositorio.buscarPorHashParaActualizar",
            "TokenDeRecuperacionRepositorio.buscarPorHashParaActualizar");

    @ArchTest
    static final ArchRule repositoriosSiempreConTenant =
            ReglasDeArquitectura.repositoriosSiempreConTenant(REPOSITORIOS_EXCEPTUADOS);

    @ArchTest
    static final ArchRule agendaSinDominioDeConsultorio = ReglasDeArquitectura.agendaSinDominioDeConsultorio();

    @ArchTest
    static final ArchRule reservaSoloPorApplication = ReglasDeArquitectura.reservaSoloPorApplication();

    @ArchTest
    static final ArchRule springDataSoloEnInfraestructura = ReglasDeArquitectura.springDataSoloEnInfraestructura();
}
