package ar.sistemaodontologico.auth.application;

import ar.sistemaodontologico.auth.domain.TokenDeRecuperacion;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de persistencia de los tokens de recuperación (C-05, design D3). Se guarda solo el hash.
 * {@link #buscarPorHashParaActualizar} es la única consulta sin consultorio: la confirmación llega sin sesión y el
 * hash de 256 bits es la credencial misma (design D10).
 */
public interface TokenDeRecuperacionRepositorio {

    void guardar(TokenDeRecuperacion token, String hash);

    /** Revoca los tokens vigentes del usuario: como máximo hay uno por cuenta (design D3). */
    void revocarVigentes(ConsultorioId consultorio, UUID usuario, Instant cuando);

    /** Bloquea la fila ({@code FOR UPDATE}) para que dos confirmaciones simultáneas no usen el mismo token. */
    Optional<TokenDeRecuperacion> buscarPorHashParaActualizar(String hash);

    void marcarUsado(ConsultorioId consultorio, UUID id, Instant cuando);
}
