package ar.sistemaodontologico.consultorio.web;

import ar.sistemaodontologico.consultorio.application.GestionarPrestaciones;
import ar.sistemaodontologico.consultorio.domain.TipoDePrestacion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/v1/prestaciones} (US-010, design D8 y D10). No hay {@code DELETE}: la ruta existe con otros métodos,
 * así que Spring responde 405 {@code METODO_NO_PERMITIDO} (RN-AG-07).
 */
@RestController
@RequestMapping("/api/v1/prestaciones")
class ControladorDePrestaciones {

    /**
     * La duración la valida el dominio, no Bean Validation, para que una duración inválida responda
     * {@code DURACION_INVALIDA} y no {@code VALIDACION} (spec prestaciones).
     */
    record PrestacionRequest(
            @NotBlank @Size(max = TipoDePrestacion.LARGO_MAXIMO_NOMBRE) String nombre,
            @NotNull Integer duracionMin,
            Boolean visiblePublica,
            @Min(0) Integer orden) {

        GestionarPrestaciones.Pedido aPedido() {
            return new GestionarPrestaciones.Pedido(nombre, duracionMin, visiblePublica, orden);
        }
    }

    record PrestacionResponse(UUID id, String nombre, int duracionMin, boolean activa, boolean visiblePublica,
            int orden) {

        static PrestacionResponse de(TipoDePrestacion prestacion) {
            return new PrestacionResponse(prestacion.id(), prestacion.nombre(), prestacion.duracionMin(),
                    prestacion.activa(), prestacion.visiblePublica(), prestacion.orden());
        }
    }

    private final GestionarPrestaciones prestaciones;

    ControladorDePrestaciones(GestionarPrestaciones prestaciones) {
        this.prestaciones = prestaciones;
    }

    @GetMapping
    List<PrestacionResponse> listar(@RequestParam(defaultValue = "false") boolean incluirInactivas) {
        return prestaciones.listar(incluirInactivas).stream().map(PrestacionResponse::de).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    PrestacionResponse crear(@Valid @RequestBody PrestacionRequest pedido) {
        return PrestacionResponse.de(prestaciones.crear(pedido.aPedido()));
    }

    @GetMapping("/{id}")
    PrestacionResponse consultar(@PathVariable UUID id) {
        return PrestacionResponse.de(prestaciones.consultar(id));
    }

    @PutMapping("/{id}")
    PrestacionResponse editar(@PathVariable UUID id, @Valid @RequestBody PrestacionRequest pedido) {
        return PrestacionResponse.de(prestaciones.editar(id, pedido.aPedido()));
    }

    @PostMapping("/{id}/desactivar")
    PrestacionResponse desactivar(@PathVariable UUID id) {
        return PrestacionResponse.de(prestaciones.desactivar(id));
    }

    @PostMapping("/{id}/activar")
    PrestacionResponse activar(@PathVariable UUID id) {
        return PrestacionResponse.de(prestaciones.activar(id));
    }
}
