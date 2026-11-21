package ar.sistemaodontologico.agenda.domain;

import ar.sistemaodontologico.compartido.domain.error.ConflictoDeNegocio;
import java.time.Instant;

/**
 * Transiciones de estado del turno como función pura (spec turnos, design D3). {@code ahora} entra como parámetro: el
 * dominio no lee el reloj.
 */
public final class MaquinaDeEstadosTurno {

    public static final String CODIGO_TRANSICION_INVALIDA = "TRANSICION_INVALIDA";

    private MaquinaDeEstadosTurno() {
    }

    /** El estado que resulta de aplicar la acción, o {@code TRANSICION_INVALIDA} si no corresponde. */
    public static EstadoTurno siguiente(EstadoTurno actual, AccionTurno accion, Instant inicio, Instant ahora) {
        return switch (accion) {
            case CONFIRMAR -> exigir(actual == EstadoTurno.RESERVADO, EstadoTurno.CONFIRMADO);
            case CANCELAR -> exigir(actual.esActivo(), EstadoTurno.CANCELADO);
            // Ausente solo cuando el turno ya empezó.
            case MARCAR_AUSENTE -> exigir(actual.esActivo() && !ahora.isBefore(inicio), EstadoTurno.AUSENTE);
            case REPROGRAMAR, AJUSTAR_DURACION -> exigir(actual.esActivo(), actual);
        };
    }

    private static EstadoTurno exigir(boolean permitida, EstadoTurno resultado) {
        if (!permitida) {
            throw new ConflictoDeNegocio(CODIGO_TRANSICION_INVALIDA, "El turno no admite ese cambio en su estado actual");
        }
        return resultado;
    }
}
