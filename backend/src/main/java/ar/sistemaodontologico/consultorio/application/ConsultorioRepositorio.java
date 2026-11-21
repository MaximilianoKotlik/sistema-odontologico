package ar.sistemaodontologico.consultorio.application;

import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import ar.sistemaodontologico.consultorio.domain.ConfiguracionDelConsultorio;
import ar.sistemaodontologico.consultorio.domain.DatosDelConsultorio;
import ar.sistemaodontologico.consultorio.domain.NuevoConsultorio;
import java.util.Optional;

/**
 * Puerto de persistencia del consultorio. Es la raíz del tenant: se busca por su propio id o por slug, por eso
 * está en {@code REPOSITORIOS_EXCEPTUADOS} de {@code ArchitectureTest} (C-02, design D9).
 */
public interface ConsultorioRepositorio extends SlugsEnUso {

    void guardarNuevo(NuevoConsultorio consultorio);

    Optional<ConfiguracionDelConsultorio> buscarConfiguracion(ConsultorioId id);

    /** El consultorio de una ruta pública (C-15): el slug es la única fuente del tenant sin sesión. */
    Optional<ConsultorioId> buscarIdPorSlug(String slug);

    /**
     * Reemplaza los datos editables si la versión no cambió desde que se leyeron; si cambió, lanza
     * {@code OptimisticLockingFailureException} (409 {@code CONFLICTO_CONCURRENCIA}, RN-GL-02).
     */
    void actualizarDatos(ConsultorioId id, DatosDelConsultorio datos, long versionLeida);
}
