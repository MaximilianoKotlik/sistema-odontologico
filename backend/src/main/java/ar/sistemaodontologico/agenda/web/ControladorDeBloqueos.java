package ar.sistemaodontologico.agenda.web;

import ar.sistemaodontologico.agenda.application.Autor;
import ar.sistemaodontologico.agenda.application.ConsultarAgenda;
import ar.sistemaodontologico.agenda.application.CrearBloqueo;
import ar.sistemaodontologico.agenda.application.EliminarBloqueo;
import ar.sistemaodontologico.agenda.domain.BloqueoId;
import ar.sistemaodontologico.agenda.web.RespuestasDeAgenda.BloqueoResponse;
import ar.sistemaodontologico.compartido.application.SesionActual;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/v1/bloqueos} (C-12, spec api-de-turnos-y-bloqueos, design D8; US-012). Un bloqueo que pisa turnos
 * activos es 409 {@code BLOQUEO_CON_TURNOS} con su lista (RN-AG-05); uno de otro consultorio es 404 (RN-TE-04).
 */
@RestController
@RequestMapping("/api/v1/bloqueos")
class ControladorDeBloqueos {

    record CrearBloqueoRequest(@NotNull Instant inicio, @NotNull Instant fin, String motivo) {
    }

    private final ConsultarAgenda consultar;
    private final CrearBloqueo crear;
    private final EliminarBloqueo eliminar;
    private final SesionActual sesion;

    ControladorDeBloqueos(ConsultarAgenda consultar, CrearBloqueo crear, EliminarBloqueo eliminar,
            SesionActual sesion) {
        this.consultar = consultar;
        this.crear = crear;
        this.eliminar = eliminar;
        this.sesion = sesion;
    }

    /** Sin {@code desde}, los que siguen vigentes desde ahora. */
    @GetMapping
    List<BloqueoResponse> listar(@RequestParam Optional<Instant> desde) {
        return consultar.bloqueosVigentes(desde).stream().map(BloqueoResponse::de).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    BloqueoResponse crear(@Valid @RequestBody CrearBloqueoRequest pedido) {
        return BloqueoResponse.de(crear.crear(pedido.inicio(), pedido.fin(), pedido.motivo(), odontologo()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void eliminar(@PathVariable UUID id) {
        eliminar.eliminar(new BloqueoId(id), odontologo());
    }

    private Autor odontologo() {
        return Autor.odontologo(sesion.usuarioId(), sesion.ip());
    }
}
