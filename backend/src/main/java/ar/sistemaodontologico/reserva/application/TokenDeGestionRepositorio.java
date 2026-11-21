package ar.sistemaodontologico.reserva.application;

import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Tokens de gestión del paciente (design D3). Las búsquedas por hash no reciben consultorio porque el link llega sin
 * sesión y el hash de 256 bits es la credencial: el consultorio sale de la fila encontrada.
 */
public interface TokenDeGestionRepositorio {

    void insertar(ConsultorioId consultorio, UUID id, UUID turno, String hash);

    /** Para la vista: turno activo o cancelado que todavía no terminó, aunque el token esté revocado. */
    Optional<TurnoPorToken> buscarConsultable(String hash, Instant ahora);

    /** Para modificar: turno activo que todavía no terminó y token sin revocar. */
    Optional<TurnoPorToken> buscarModificable(String hash, Instant ahora);

    void revocar(ConsultorioId consultorio, UUID turno, Instant ahora);
}
