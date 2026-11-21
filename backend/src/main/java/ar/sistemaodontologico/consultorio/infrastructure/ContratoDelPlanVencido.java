package ar.sistemaodontologico.consultorio.infrastructure;

import io.swagger.v3.oas.models.responses.ApiResponse;
import java.util.Locale;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;

/**
 * Documenta en el contrato OpenAPI el 403 {@code PLAN_VENCIDO} de cada escritura que bloquea la guarda (C-20, design
 * D8), con la misma regla que {@link FiltroDePlanVencido}: así el contrato no se desactualiza al sumar endpoints.
 */
@Component
class ContratoDelPlanVencido implements OpenApiCustomizer {

    static final String DESCRIPCION =
            "PLAN_VENCIDO: el plan del consultorio está vencido y la agenda está en solo lectura (RN-PL-03)";

    @Override
    public void customise(io.swagger.v3.oas.models.OpenAPI contrato) {
        if (contrato.getPaths() == null) {
            return;
        }
        contrato.getPaths().forEach((ruta, item) -> item.readOperationsMap().forEach((metodo, operacion) -> {
            if (FiltroDePlanVencido.esEscrituraBloqueable(metodo.name().toUpperCase(Locale.ROOT),
                    PathContainer.parsePath(ruta))) {
                operacion.getResponses().addApiResponse("403", new ApiResponse().description(DESCRIPCION));
            }
        }));
    }
}
