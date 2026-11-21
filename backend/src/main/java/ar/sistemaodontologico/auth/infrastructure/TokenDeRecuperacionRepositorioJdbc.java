package ar.sistemaodontologico.auth.infrastructure;

import ar.sistemaodontologico.auth.application.TokenDeRecuperacionRepositorio;
import ar.sistemaodontologico.auth.domain.TokenDeRecuperacion;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Tokens de recuperación con JdbcClient (C-05, design D3). */
@Repository
class TokenDeRecuperacionRepositorioJdbc implements TokenDeRecuperacionRepositorio {

    private final JdbcClient jdbc;

    TokenDeRecuperacionRepositorioJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void guardar(TokenDeRecuperacion token, String hash) {
        jdbc.sql("""
                INSERT INTO token_recuperacion (id, consultorio_id, usuario_id, token_hash, expira_at)
                VALUES (:id, :consultorio, :usuario, :hash, :expira)""")
                .param("id", token.id())
                .param("consultorio", token.consultorio().valor())
                .param("usuario", token.usuario())
                .param("hash", hash)
                .param("expira", marca(token.expiraAt()))
                .update();
    }

    @Override
    public void revocarVigentes(ConsultorioId consultorio, UUID usuario, Instant cuando) {
        jdbc.sql("""
                UPDATE token_recuperacion SET revocado_at = :cuando
                WHERE consultorio_id = :consultorio AND usuario_id = :usuario
                  AND usado_at IS NULL AND revocado_at IS NULL""")
                .param("cuando", marca(cuando))
                .param("consultorio", consultorio.valor())
                .param("usuario", usuario)
                .update();
    }

    @Override
    public Optional<TokenDeRecuperacion> buscarPorHashParaActualizar(String hash) {
        return jdbc.sql("""
                SELECT id, consultorio_id, usuario_id, expira_at, usado_at, revocado_at
                FROM token_recuperacion WHERE token_hash = :hash FOR UPDATE""")
                .param("hash", hash)
                .query((fila, n) -> token(fila))
                .optional();
    }

    @Override
    public void marcarUsado(ConsultorioId consultorio, UUID id, Instant cuando) {
        jdbc.sql("UPDATE token_recuperacion SET usado_at = :cuando WHERE consultorio_id = :consultorio AND id = :id")
                .param("cuando", marca(cuando))
                .param("consultorio", consultorio.valor())
                .param("id", id)
                .update();
    }

    private static TokenDeRecuperacion token(ResultSet fila) throws SQLException {
        return new TokenDeRecuperacion(
                fila.getObject("id", UUID.class),
                new ConsultorioId(fila.getObject("consultorio_id", UUID.class)),
                fila.getObject("usuario_id", UUID.class),
                fila.getTimestamp("expira_at").toInstant(),
                instante(fila.getTimestamp("usado_at")),
                instante(fila.getTimestamp("revocado_at")));
    }

    private static Instant instante(Timestamp marca) {
        return marca == null ? null : marca.toInstant();
    }

    /** Truncado a microsegundos: PostgreSQL redondea lo que sobra y el vencimiento podría correrse hacia adelante. */
    private static Timestamp marca(Instant instante) {
        return Timestamp.from(instante.truncatedTo(ChronoUnit.MICROS));
    }
}
