package ar.sistemaodontologico.auth.infrastructure;

import ar.sistemaodontologico.auth.application.RefreshTokenRepositorio;
import ar.sistemaodontologico.auth.domain.RefreshToken;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Refresh tokens con JdbcClient. Solo se guarda el hash; se revocan, nunca se borran. */
@Repository
class RefreshTokenRepositorioJdbc implements RefreshTokenRepositorio {

    private final JdbcClient jdbc;

    RefreshTokenRepositorioJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void guardar(UUID id, ConsultorioId consultorio, UUID usuario, String hash, Instant expiraAt) {
        jdbc.sql("""
                INSERT INTO refresh_token (id, consultorio_id, usuario_id, token_hash, expira_at)
                VALUES (?, ?, ?, ?, ?)""")
                .params(id, consultorio.valor(), usuario, hash, Timestamp.from(expiraAt))
                .update();
    }

    @Override
    public Optional<RefreshToken> buscarPorHashParaActualizar(String hash) {
        return jdbc.sql("""
                SELECT id, consultorio_id, usuario_id, expira_at, revocado_at
                FROM refresh_token WHERE token_hash = ? FOR UPDATE""")
                .param(hash)
                .query((fila, n) -> new RefreshToken(
                        fila.getObject("id", UUID.class),
                        new ConsultorioId(fila.getObject("consultorio_id", UUID.class)),
                        fila.getObject("usuario_id", UUID.class),
                        fila.getTimestamp("expira_at").toInstant(),
                        Optional.ofNullable(fila.getTimestamp("revocado_at")).map(Timestamp::toInstant).orElse(null)))
                .optional();
    }

    @Override
    public void revocar(ConsultorioId consultorio, UUID id, Instant cuando) {
        jdbc.sql("UPDATE refresh_token SET revocado_at = ? WHERE consultorio_id = ? AND id = ? AND revocado_at IS NULL")
                .params(Timestamp.from(cuando), consultorio.valor(), id)
                .update();
    }

    @Override
    public void revocarTodos(ConsultorioId consultorio, UUID usuario, Instant cuando) {
        jdbc.sql("""
                UPDATE refresh_token SET revocado_at = ?
                WHERE consultorio_id = ? AND usuario_id = ? AND revocado_at IS NULL""")
                .params(Timestamp.from(cuando), consultorio.valor(), usuario)
                .update();
    }
}
