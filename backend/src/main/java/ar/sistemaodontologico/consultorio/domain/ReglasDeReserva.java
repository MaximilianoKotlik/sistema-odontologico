package ar.sistemaodontologico.consultorio.domain;

import ar.sistemaodontologico.compartido.domain.error.ValidacionDeNegocio;
import java.util.Map;
import java.util.Set;

/**
 * Reglas con las que el consultorio toma reservas (knowledge-base/04 §Consultorio, C-08 design D9). Los rangos los
 * valida también Bean Validation en la API; acá quedan como invariante del dominio.
 *
 * @param anticipacionCancelacionMin hasta cuántos minutos antes del turno el paciente puede cancelar (0 a 10080)
 * @param anticipacionReservaMin cuántos minutos antes, como mínimo, se puede reservar (0 o más)
 * @param horizonteReservaDias hasta cuántos días adelante se puede reservar (1 a 365)
 * @param pasoHuecosMin cada cuántos minutos se ofrecen huecos (5, 10, 15, 20 o 30)
 */
public record ReglasDeReserva(int anticipacionCancelacionMin, int anticipacionReservaMin, int horizonteReservaDias,
        int pasoHuecosMin) {

    public static final int CANCELACION_MAXIMA_MIN = 10_080;
    /** Tope de aplicación aprobado por Maxi en #15 (la base solo exige {@code > 0}). */
    public static final int HORIZONTE_MAXIMO_DIAS = 365;
    public static final Set<Integer> PASOS_PERMITIDOS = Set.of(5, 10, 15, 20, 30);
    public static final String CODIGO_ANTICIPACION_FUERA_DE_HORIZONTE = "ANTICIPACION_FUERA_DE_HORIZONTE";

    /** Valores por defecto de un consultorio nuevo (V1). */
    public static final ReglasDeReserva POR_DEFECTO = new ReglasDeReserva(1440, 120, 60, 15);

    private static final int MINUTOS_POR_DIA = 1440;

    public ReglasDeReserva {
        if (anticipacionCancelacionMin < 0 || anticipacionCancelacionMin > CANCELACION_MAXIMA_MIN
                || anticipacionReservaMin < 0
                || horizonteReservaDias < 1 || horizonteReservaDias > HORIZONTE_MAXIMO_DIAS
                || !PASOS_PERMITIDOS.contains(pasoHuecosMin)) {
            throw new ValidacionDeNegocio("VALIDACION", "Hay datos inválidos");
        }
        // Pedido de Maxi en #15: si la anticipación no entra en el horizonte, no queda ningún hueco reservable.
        if (anticipacionReservaMin >= horizonteReservaDias * MINUTOS_POR_DIA) {
            throw new ValidacionDeNegocio(CODIGO_ANTICIPACION_FUERA_DE_HORIZONTE,
                    "La anticipación de reserva tiene que ser menor que el horizonte",
                    Map.of("campo", "anticipacionReservaMin"));
        }
    }
}
