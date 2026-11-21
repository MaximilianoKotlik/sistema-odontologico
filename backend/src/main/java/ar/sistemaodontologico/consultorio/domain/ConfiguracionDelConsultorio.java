package ar.sistemaodontologico.consultorio.domain;

import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Configuración completa leída de la base: los datos editables más el plan, que solo se muestra.
 *
 * @param version la columna {@code version} del consultorio, para detectar guardados concurrentes (RN-GL-02)
 */
public record ConfiguracionDelConsultorio(ConsultorioId id, DatosDelConsultorio datos, String plan,
        LocalDate pruebaHasta, long version) {

    public ConfiguracionDelConsultorio {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(datos, "datos");
        Objects.requireNonNull(plan, "plan");
    }
}
