package ar.sistemaodontologico.consultorio.web;

import ar.sistemaodontologico.consultorio.application.ConsultarConfiguracion;
import ar.sistemaodontologico.consultorio.application.EditarConfiguracion;
import ar.sistemaodontologico.consultorio.domain.ConfiguracionDelConsultorio;
import ar.sistemaodontologico.consultorio.domain.DatosDelConsultorio;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET/PUT /api/v1/consultorio} (US-004, design D10). El consultorio es siempre el de la sesión: el body no
 * lleva id, y {@code plan} o {@code pruebaHasta} se ignoran porque no están en el request.
 */
@RestController
@RequestMapping("/api/v1/consultorio")
class ControladorDeConfiguracion {

    record ConfiguracionRequest(
            @NotBlank @Size(max = 120) String nombre,
            @NotBlank @Size(max = 40) String slug,
            @Size(max = 200) String direccion,
            // Obligatorio desde los mockups de C-09 (decisión de Maxi en #15).
            @NotBlank @Size(max = 30) String telefonoContacto,
            @NotBlank @Size(max = 50) String zonaHoraria,
            @NotNull @Min(0) @Max(10_080) Integer anticipacionCancelacionMin,
            @NotNull @Min(0) Integer anticipacionReservaMin,
            @NotNull @Min(1) @Max(365) Integer horizonteReservaDias,
            @NotNull @PasoDeHuecos Integer pasoHuecosMin,
            @NotNull Boolean reservaPublicaActiva) {
    }

    record ConfiguracionResponse(String nombre, String slug, String direccion, String telefonoContacto,
            String zonaHoraria, int anticipacionCancelacionMin, int anticipacionReservaMin, int horizonteReservaDias,
            int pasoHuecosMin, boolean reservaPublicaActiva, String plan, LocalDate pruebaHasta) {

        static ConfiguracionResponse de(ConfiguracionDelConsultorio configuracion) {
            DatosDelConsultorio datos = configuracion.datos();
            return new ConfiguracionResponse(datos.nombre(), datos.slug(), datos.direccion(), datos.telefonoContacto(),
                    datos.zonaHoraria(), datos.reglas().anticipacionCancelacionMin(),
                    datos.reglas().anticipacionReservaMin(), datos.reglas().horizonteReservaDias(),
                    datos.reglas().pasoHuecosMin(), datos.reservaPublicaActiva(), configuracion.plan(),
                    configuracion.pruebaHasta());
        }
    }

    private final ConsultarConfiguracion consulta;
    private final EditarConfiguracion edicion;

    ControladorDeConfiguracion(ConsultarConfiguracion consulta, EditarConfiguracion edicion) {
        this.consulta = consulta;
        this.edicion = edicion;
    }

    @GetMapping
    ConfiguracionResponse consultar() {
        return ConfiguracionResponse.de(consulta.consultar());
    }

    @PutMapping
    ConfiguracionResponse editar(@Valid @RequestBody ConfiguracionRequest pedido) {
        return ConfiguracionResponse.de(edicion.editar(new EditarConfiguracion.Pedido(pedido.nombre().trim(),
                pedido.slug().trim(), pedido.direccion(), pedido.telefonoContacto(), pedido.zonaHoraria().trim(),
                pedido.anticipacionCancelacionMin(), pedido.anticipacionReservaMin(), pedido.horizonteReservaDias(),
                pedido.pasoHuecosMin(), pedido.reservaPublicaActiva())));
    }
}
