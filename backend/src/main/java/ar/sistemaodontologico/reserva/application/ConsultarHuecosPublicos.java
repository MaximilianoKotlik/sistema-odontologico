package ar.sistemaodontologico.reserva.application;

import ar.sistemaodontologico.compartido.domain.error.RecursoNoEncontrado;
import ar.sistemaodontologico.consultorio.application.PrestacionRepositorio;
import ar.sistemaodontologico.consultorio.domain.ConfiguracionDelConsultorio;
import ar.sistemaodontologico.consultorio.domain.TipoDePrestacion;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Huecos ofrecibles para una prestación visible del consultorio (RN-RP-01, design D5). */
@Service
public class ConsultarHuecosPublicos {

    private final ConsultorioPublico publico;
    private final PrestacionRepositorio prestaciones;

    ConsultarHuecosPublicos(ConsultorioPublico publico, PrestacionRepositorio prestaciones) {
        this.publico = publico;
        this.prestaciones = prestaciones;
    }

    public HuecosPublicos consultar(String slug, UUID prestacionId, LocalDate desde, LocalDate hasta) {
        ConfiguracionDelConsultorio consultorio = publico.porSlug(slug);
        publico.exigirQueTomeTurnos(consultorio);
        TipoDePrestacion prestacion = prestacionOfrecida(prestaciones, consultorio, prestacionId);
        return publico.huecos(consultorio, prestacion.duracionMin(), desde, hasta, Optional.empty());
    }

    /** Activa, visible y del consultorio; si no, como inexistente (spec reserva-publica). */
    static TipoDePrestacion prestacionOfrecida(PrestacionRepositorio prestaciones,
            ConfiguracionDelConsultorio consultorio, UUID prestacionId) {
        return prestaciones.buscar(consultorio.id(), prestacionId)
                .filter(prestacion -> prestacion.activa() && prestacion.visiblePublica())
                .orElseThrow(() -> new RecursoNoEncontrado("prestación"));
    }
}
