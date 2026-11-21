package ar.sistemaodontologico.plan.web;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import ar.sistemaodontologico.plan.application.ConsultarPlanesPublicos;
import ar.sistemaodontologico.plan.domain.CatalogoDePlanes;
import ar.sistemaodontologico.plan.domain.Plan;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/v1/public/planes} (US-060, design D3): catálogo público sin autenticación. La ruta ya es pública en
 * la cadena de seguridad de C-04 ({@code /api/v1/public/**}).
 */
@RestController
class ControladorDePlanesPublicos {

    /** {@code actualizadoEn}: instante UTC (ISO-8601) de la última actualización de precios. */
    record PlanesPublicosResponse(
            @Schema(requiredMode = REQUIRED) List<PlanPublicoResponse> planes,
            @Schema(requiredMode = REQUIRED) PruebaResponse prueba,
            @Schema(requiredMode = REQUIRED) @JsonFormat(shape = JsonFormat.Shape.STRING) Instant actualizadoEn) {
    }

    /** {@code importeARS}: texto decimal exacto con dos decimales, impuestos incluidos (design D2). */
    record PlanPublicoResponse(
            @Schema(requiredMode = REQUIRED) String id,
            @Schema(requiredMode = REQUIRED) String nombre,
            @JsonInclude(JsonInclude.Include.NON_NULL) String descripcion,
            @Schema(requiredMode = REQUIRED) List<String> incluye,
            @Schema(requiredMode = REQUIRED) @JsonProperty("importeARS") String importeArs,
            @Schema(requiredMode = REQUIRED) String moneda,
            @Schema(requiredMode = REQUIRED) boolean impuestosIncluidos) {
    }

    record PruebaResponse(
            @Schema(requiredMode = REQUIRED) int dias,
            @Schema(requiredMode = REQUIRED) boolean requiereMedioDePago) {
    }

    private final ConsultarPlanesPublicos consultar;

    ControladorDePlanesPublicos(ConsultarPlanesPublicos consultar) {
        this.consultar = consultar;
    }

    @GetMapping("/api/v1/public/planes")
    PlanesPublicosResponse planes() {
        CatalogoDePlanes catalogo = consultar.consultar();
        return new PlanesPublicosResponse(
                catalogo.planes().stream().map(ControladorDePlanesPublicos::plan).toList(),
                new PruebaResponse(catalogo.prueba().dias(), catalogo.prueba().requiereMedioDePago()),
                catalogo.actualizadoEn());
    }

    private static PlanPublicoResponse plan(Plan plan) {
        String importe = plan.importeArs().setScale(2, RoundingMode.UNNECESSARY).toPlainString();
        return new PlanPublicoResponse(plan.id(), plan.nombre(), plan.descripcion(), plan.incluye(), importe,
                "ARS", true);
    }
}
