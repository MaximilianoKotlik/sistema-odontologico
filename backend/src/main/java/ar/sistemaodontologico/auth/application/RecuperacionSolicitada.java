package ar.sistemaodontologico.auth.application;

import java.util.UUID;

/**
 * Evento interno: se emitió un token de recuperación y hay que enviarlo por email después del commit (design D4).
 * Lleva el token en claro solo en memoria; {@link #toString()} no lo muestra ni muestra el email.
 */
public record RecuperacionSolicitada(UUID usuario, String email, String token) {

    @Override
    public String toString() {
        return "RecuperacionSolicitada[usuario=" + usuario + "]";
    }
}
