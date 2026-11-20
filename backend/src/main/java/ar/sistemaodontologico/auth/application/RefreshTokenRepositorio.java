package ar.sistemaodontologico.auth.application;

import ar.sistemaodontologico.auth.domain.RefreshToken;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de persistencia de los refresh tokens. {@link #buscarPorHashParaActualizar} es la única consulta sin
 * consultorio: el refresh llega sin access token y el hash de 256 bits es la credencial (design D9).
 */
public interface RefreshTokenRepositorio {

    void guardar(UUID id, ConsultorioId consultorio, UUID usuario, String hash, Instant expiraAt);

    /** Bloquea la fila ({@code FOR UPDATE}) para que dos rotaciones simultáneas no emitan dos sesiones. */
    Optional<RefreshToken> buscarPorHashParaActualizar(String hash);

    void revocar(ConsultorioId consultorio, UUID id, Instant cuando);

    /** Revoca todos los refresh vigentes del usuario (detección de reuso, RN-AU-02). */
    void revocarTodos(ConsultorioId consultorio, UUID usuario, Instant cuando);
}
