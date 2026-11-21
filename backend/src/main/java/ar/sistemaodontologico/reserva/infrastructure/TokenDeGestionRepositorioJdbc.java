package ar.sistemaodontologico.reserva.infrastructure;

import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import ar.sistemaodontologico.reserva.application.TokenDeGestionRepositorio;
import ar.sistemaodontologico.reserva.application.TurnoPorToken;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Tokens con JdbcClient. La vigencia se deriva del turno en la misma consulta que busca por hash (design D3). */
@Repository
class TokenDeGestionRepositorioJdbc implements TokenDeGestionRepositorio {

    private static final String CONSULTA = """
            SELECT g.consultorio_id, t.id AS turno_id, t.paciente_id, t.tipo_prestacion_id,
                   p.nombre AS prestacion, t.inicio, t.fin, t.duracion_min, t.estado, t.cancelado_por, t.version,
                   pa.nombre AS paciente_nombre, pa.apellido AS paciente_apellido
              FROM token_gestion_turno g
              JOIN turno t ON t.consultorio_id = g.consultorio_id AND t.id = g.turno_id
              JOIN paciente pa ON pa.consultorio_id = t.consultorio_id AND pa.id = t.paciente_id
              JOIN tipo_prestacion p ON p.consultorio_id = t.consultorio_id AND p.id = t.tipo_prestacion_id
             WHERE g.token_hash = :hash AND t.fin > :ahora""";

    private final JdbcClient jdbc;

    TokenDeGestionRepositorioJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insertar(ConsultorioId consultorio, UUID id, UUID turno, String hash) {
        jdbc.sql("""
                INSERT INTO token_gestion_turno (id, consultorio_id, turno_id, token_hash)
                VALUES (:id, :consultorio, :turno, :hash)""")
                .param("id", id)
                .param("consultorio", consultorio.valor())
                .param("turno", turno)
                .param("hash", hash)
                .update();
    }

    @Override
    public Optional<TurnoPorToken> buscarConsultable(String hash, Instant ahora) {
        return buscar(CONSULTA + " AND t.estado IN ('RESERVADO', 'CONFIRMADO', 'CANCELADO')", hash, ahora);
    }

    @Override
    public Optional<TurnoPorToken> buscarModificable(String hash, Instant ahora) {
        return buscar(CONSULTA + " AND t.estado IN ('RESERVADO', 'CONFIRMADO') AND g.revocado_at IS NULL", hash, ahora);
    }

    @Override
    public void revocar(ConsultorioId consultorio, UUID turno, Instant ahora) {
        jdbc.sql("""
                UPDATE token_gestion_turno SET revocado_at = :ahora
                 WHERE consultorio_id = :consultorio AND turno_id = :turno AND revocado_at IS NULL""")
                .param("ahora", Timestamp.from(ahora))
                .param("consultorio", consultorio.valor())
                .param("turno", turno)
                .update();
    }

    private Optional<TurnoPorToken> buscar(String sql, String hash, Instant ahora) {
        return jdbc.sql(sql)
                .param("hash", hash)
                .param("ahora", Timestamp.from(ahora))
                .query((fila, numero) -> turno(fila))
                .optional();
    }

    private static TurnoPorToken turno(ResultSet fila) throws SQLException {
        return new TurnoPorToken(
                new ConsultorioId(fila.getObject("consultorio_id", UUID.class)),
                fila.getObject("turno_id", UUID.class),
                fila.getObject("paciente_id", UUID.class),
                fila.getObject("tipo_prestacion_id", UUID.class),
                fila.getString("prestacion"),
                fila.getTimestamp("inicio").toInstant(),
                fila.getTimestamp("fin").toInstant(),
                fila.getInt("duracion_min"),
                fila.getString("estado"),
                fila.getString("cancelado_por"),
                fila.getLong("version"),
                fila.getString("paciente_nombre"),
                fila.getString("paciente_apellido"));
    }
}
