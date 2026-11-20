package ar.sistemaodontologico.auth.domain;

import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Token de recuperación guardado (sin su valor, solo el hash). Reglas puras de vigencia: dura 30 minutos
 * y deja de valer al usarse o al revocarse por una solicitud nueva (design D3).
 */
public record TokenDeRecuperacion(UUID id, ConsultorioId consultorio, UUID usuario, Instant expiraAt,
                                  Instant usadoAt, Instant revocadoAt) {

    public static final Duration VIGENCIA = Duration.ofMinutes(30);

    public static TokenDeRecuperacion emitir(UUID id, ConsultorioId consultorio, UUID usuario, Instant ahora) {
        return new TokenDeRecuperacion(id, consultorio, usuario, ahora.plus(VIGENCIA), null, null);
    }

    public boolean usado() {
        return usadoAt != null;
    }

    public boolean revocado() {
        return revocadoAt != null;
    }

    public boolean vencido(Instant ahora) {
        return !ahora.isBefore(expiraAt);
    }

    public boolean vigente(Instant ahora) {
        return !usado() && !revocado() && !vencido(ahora);
    }
}
