package ar.sistemaodontologico.auth.domain;

import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import java.time.Instant;
import java.util.UUID;

/** Refresh guardado (sin su valor, solo el hash). Reglas puras de vigencia (design D4). */
public record RefreshToken(UUID id, ConsultorioId consultorio, UUID usuario, Instant expiraAt, Instant revocadoAt) {

    public boolean revocado() {
        return revocadoAt != null;
    }

    public boolean vencido(Instant ahora) {
        return !ahora.isBefore(expiraAt);
    }
}
