package ar.sistemaodontologico.consultorio.web;

import ar.sistemaodontologico.consultorio.application.ConsultarPlan;
import ar.sistemaodontologico.consultorio.domain.EstadoDelPlan;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/v1/plan} (C-20, design D5; US-061): el plan efectivo del consultorio de la sesión. Los días se
 * calculan acá, en la zona del consultorio (RN-GL-04). No hay ninguna ruta que cambie el plan (design D3).
 */
@RestController
class ControladorDePlan {

    /**
     * @param plan el plan efectivo: {@code VENCIDO} también si la prueba terminó y la tarea diaria todavía no corrió
     * @param diasDePruebaRestantes con hoy y {@code pruebaHasta} incluidos; {@code null} fuera de una prueba vigente
     */
    record PlanResponse(String plan, LocalDate pruebaHasta, Integer diasDePruebaRestantes, boolean soloLectura) {

        static PlanResponse de(EstadoDelPlan estado) {
            return new PlanResponse(estado.planEfectivo().name(), estado.pruebaHasta(),
                    estado.diasDePruebaRestantes(), estado.soloLectura());
        }
    }

    private final ConsultarPlan consulta;

    ControladorDePlan(ConsultarPlan consulta) {
        this.consulta = consulta;
    }

    @GetMapping("/api/v1/plan")
    PlanResponse consultar() {
        return PlanResponse.de(consulta.consultar());
    }
}
