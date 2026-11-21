package ar.sistemaodontologico.reserva.application;

import java.time.Instant;
import java.time.ZoneId;

/**
 * Evento para enviar la confirmación después del commit (DD-26, design D12). Lleva el email del pedido y el token en
 * claro, que no se persisten: por eso no aparecen en {@link #toString()}.
 */
public record ReservaConfirmada(String email, String consultorio, Instant inicio, ZoneId zona, String token) {

    @Override
    public String toString() {
        return "ReservaConfirmada[***]";
    }
}
