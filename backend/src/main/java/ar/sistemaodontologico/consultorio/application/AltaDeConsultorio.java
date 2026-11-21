package ar.sistemaodontologico.consultorio.application;

import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import ar.sistemaodontologico.compartido.domain.GeneradorDeIds;
import ar.sistemaodontologico.consultorio.domain.NuevoConsultorio;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Service;

/**
 * Da de alta un consultorio en prueba con un slug libre (US-001). Corre dentro de la transacción de quien lo
 * llama (el registro de C-04), así un fallo posterior no deja el consultorio creado.
 */
@Service
public class AltaDeConsultorio {

    /** Consultorio recién creado, con lo que audita el registro (C-05, design D9). */
    public record ConsultorioCreado(ConsultorioId id, String slug, String plan, LocalDate pruebaHasta) {
    }

    static final String PLAN_INICIAL = "PRUEBA";

    private final ConsultorioRepositorio consultorios;
    private final AsignadorDeSlugUnico slugs;
    private final GeneradorDeIds ids;
    private final Clock reloj;

    public AltaDeConsultorio(ConsultorioRepositorio consultorios, AsignadorDeSlugUnico slugs, GeneradorDeIds ids,
            Clock reloj) {
        this.consultorios = consultorios;
        this.slugs = slugs;
        this.ids = ids;
        this.reloj = reloj;
    }

    public ConsultorioCreado darDeAlta(String nombre) {
        ConsultorioId id = new ConsultorioId(ids.nuevo());
        String slug = slugs.asignar(nombre);
        // "Hoy" en la zona del consultorio, no en UTC (RN-TE-05).
        LocalDate hoy = LocalDate.now(reloj.withZone(ZoneId.of(NuevoConsultorio.ZONA_POR_DEFECTO)));
        LocalDate pruebaHasta = hoy.plusDays(NuevoConsultorio.DIAS_DE_PRUEBA);
        consultorios.guardarNuevo(new NuevoConsultorio(id, nombre, slug, pruebaHasta));
        return new ConsultorioCreado(id, slug, PLAN_INICIAL, pruebaHasta);
    }
}
