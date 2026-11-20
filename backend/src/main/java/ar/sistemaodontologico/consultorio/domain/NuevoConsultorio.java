package ar.sistemaodontologico.consultorio.domain;

import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import java.time.LocalDate;
import java.util.Objects;

/** Consultorio recién registrado: en prueba hasta {@code pruebaHasta} (RN-PL-02), con el resto de los valores por defecto. */
public record NuevoConsultorio(ConsultorioId id, String nombre, String slug, LocalDate pruebaHasta) {

    /** Zona por defecto de un consultorio nuevo (RN-TE-05). */
    public static final String ZONA_POR_DEFECTO = "America/Argentina/Buenos_Aires";

    /** Duración de la prueba gratuita (RN-PL-02). */
    public static final int DIAS_DE_PRUEBA = 30;

    public NuevoConsultorio {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(nombre, "nombre");
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(pruebaHasta, "pruebaHasta");
    }
}
