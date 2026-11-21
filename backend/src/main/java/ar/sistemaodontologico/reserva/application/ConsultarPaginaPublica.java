package ar.sistemaodontologico.reserva.application;

import ar.sistemaodontologico.consultorio.application.PrestacionRepositorio;
import ar.sistemaodontologico.consultorio.domain.ConfiguracionDelConsultorio;
import ar.sistemaodontologico.consultorio.domain.DatosDelConsultorio;
import ar.sistemaodontologico.reserva.application.PaginaPublica.PrestacionPublica;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

/** Página pública del consultorio (US-030). Sin reserva online, responde con el teléfono y sin prestaciones (RN-RP-11). */
@Service
public class ConsultarPaginaPublica {

    private static final long SEGUNDOS_POR_DIA = 86_400L;

    private final ConsultorioPublico publico;
    private final PrestacionRepositorio prestaciones;

    ConsultarPaginaPublica(ConsultorioPublico publico, PrestacionRepositorio prestaciones) {
        this.publico = publico;
        this.prestaciones = prestaciones;
    }

    public PaginaPublica consultar(String slug) {
        ConfiguracionDelConsultorio consultorio = publico.porSlug(slug);
        DatosDelConsultorio datos = consultorio.datos();
        boolean tomaTurnos = publico.tomaTurnos(consultorio);
        List<PrestacionPublica> visibles = !tomaTurnos ? List.of() : prestaciones.listar(consultorio.id(), false).stream()
                .filter(prestacion -> prestacion.activa() && prestacion.visiblePublica())
                .map(prestacion -> new PrestacionPublica(prestacion.id(), prestacion.nombre(), prestacion.duracionMin()))
                .toList();
        LocalDate reservableHasta = publico.ahora().plusSeconds(datos.reglas().horizonteReservaDias() * SEGUNDOS_POR_DIA)
                .atZone(ConsultorioPublico.zona(consultorio)).toLocalDate();
        return new PaginaPublica(datos.nombre(), datos.direccion(), datos.telefonoContacto(), datos.zonaHoraria(),
                tomaTurnos, reservableHasta, visibles);
    }
}
