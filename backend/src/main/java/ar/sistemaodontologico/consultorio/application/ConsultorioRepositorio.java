package ar.sistemaodontologico.consultorio.application;

import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import ar.sistemaodontologico.consultorio.domain.ConfiguracionDelConsultorio;
import ar.sistemaodontologico.consultorio.domain.DatosDelConsultorio;
import ar.sistemaodontologico.consultorio.domain.NuevoConsultorio;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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

    /** Lo que hace falta para el estado efectivo del plan (C-20, design D1): una lectura por la clave primaria. */
    record PlanGuardado(String plan, LocalDate pruebaHasta, String zonaHoraria) {
    }

    Optional<PlanGuardado> buscarPlan(ConsultorioId id);

    /**
     * Pasa a {@code VENCIDO} las pruebas cuya fecha local ya pasó {@code prueba_hasta} y devuelve sus ids (C-20, design
     * D2). El {@code WHERE plan = 'PRUEBA'} la hace idempotente, también con dos corridas a la vez.
     */
    List<ConsultorioId> vencerPruebas(Instant ahora);

    /**
     * Pasa el consultorio a {@code ACTIVO} y devuelve el plan que tenía; vacío si ya estaba activo o no existe (C-20,
     * design D3). No sube {@code version}: el plan no es parte de la edición de la configuración.
     */
    Optional<String> activarPlan(ConsultorioId id);

    boolean existe(ConsultorioId id);
}
