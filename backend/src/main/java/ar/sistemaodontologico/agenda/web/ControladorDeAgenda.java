package ar.sistemaodontologico.agenda.web;

import ar.sistemaodontologico.agenda.application.AgendaDelRango;
import ar.sistemaodontologico.agenda.application.ConsultarAgenda;
import ar.sistemaodontologico.agenda.web.RespuestasDeAgenda.BloqueoResponse;
import ar.sistemaodontologico.agenda.web.RespuestasDeAgenda.TurnoResponse;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/v1/agenda?desde&hasta} (C-12, spec agenda-del-odontologo, design D2). Es lo único que guarda la
 * caché offline {@code agenda-v1} de C-06, por eso no trae datos personales de más.
 */
@RestController
@RequestMapping("/api/v1/agenda")
class ControladorDeAgenda {

    record AgendaResponse(List<TurnoResponse> turnos, List<BloqueoResponse> bloqueos) {
    }

    private final ConsultarAgenda consultar;

    ControladorDeAgenda(ConsultarAgenda consultar) {
        this.consultar = consultar;
    }

    @GetMapping
    AgendaResponse agenda(@RequestParam Instant desde, @RequestParam Instant hasta) {
        AgendaDelRango agenda = consultar.agenda(desde, hasta);
        return new AgendaResponse(agenda.turnos().stream().map(TurnoResponse::de).toList(),
                agenda.bloqueos().stream().map(BloqueoResponse::de).toList());
    }
}
