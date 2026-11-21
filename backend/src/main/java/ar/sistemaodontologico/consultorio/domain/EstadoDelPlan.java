package ar.sistemaodontologico.consultorio.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Estado efectivo del plan (C-20, design D1, aprobado por Maxi en #53): la regla única de "vencido" que usan la guarda
 * de escrituras, la consulta del plan y la reserva pública.
 *
 * <p>Está vencido si el plan es {@code VENCIDO}, o si es {@code PRUEBA} y la fecha local del consultorio ya pasó
 * {@code prueba_hasta}, que es el último día de prueba (inclusive). Así el bloqueo empieza a las 00:00 del día
 * siguiente en la zona del consultorio aunque la tarea diaria no haya corrido.
 *
 * @param plan el plan guardado en la base
 * @param pruebaHasta último día de prueba; puede faltar
 * @param hoy la fecha actual en la zona del consultorio
 */
public record EstadoDelPlan(PlanDelConsultorio plan, LocalDate pruebaHasta, LocalDate hoy) {

    public EstadoDelPlan {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(hoy, "hoy");
    }

    /** El estado en ese instante, con la fecha contada en la zona del consultorio. */
    public static EstadoDelPlan en(String plan, LocalDate pruebaHasta, ZoneId zona, Instant ahora) {
        Objects.requireNonNull(plan, "plan");
        return new EstadoDelPlan(PlanDelConsultorio.valueOf(plan), pruebaHasta, ahora.atZone(zona).toLocalDate());
    }

    public boolean vencido() {
        return plan == PlanDelConsultorio.VENCIDO || pruebaTerminada();
    }

    /** Con el plan vencido la agenda queda en solo lectura (RN-PL-03). */
    public boolean soloLectura() {
        return vencido();
    }

    /** {@code VENCIDO} si está vencido aunque la tarea todavía no lo haya pasado; si no, el plan guardado. */
    public PlanDelConsultorio planEfectivo() {
        return vencido() ? PlanDelConsultorio.VENCIDO : plan;
    }

    /** Días de prueba que quedan, con hoy y {@code prueba_hasta} incluidos; {@code null} fuera de una prueba vigente. */
    public Integer diasDePruebaRestantes() {
        if (plan != PlanDelConsultorio.PRUEBA || pruebaHasta == null || pruebaTerminada()) {
            return null;
        }
        return Math.toIntExact(ChronoUnit.DAYS.between(hoy, pruebaHasta) + 1);
    }

    private boolean pruebaTerminada() {
        return plan == PlanDelConsultorio.PRUEBA && pruebaHasta != null && hoy.isAfter(pruebaHasta);
    }
}
