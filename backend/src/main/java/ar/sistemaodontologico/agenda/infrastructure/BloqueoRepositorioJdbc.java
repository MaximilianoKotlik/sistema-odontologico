package ar.sistemaodontologico.agenda.infrastructure;

import ar.sistemaodontologico.agenda.application.BloqueoRepositorio;
import ar.sistemaodontologico.agenda.domain.Bloqueo;
import ar.sistemaodontologico.agenda.domain.BloqueoId;
import ar.sistemaodontologico.agenda.domain.Intervalo;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Bloqueos con JdbcClient (design D2); la intersección usa el índice GiST {@code ix_bloqueo_rango} de C-10. */
@Repository
class BloqueoRepositorioJdbc implements BloqueoRepositorio {

    private final JdbcClient jdbc;

    BloqueoRepositorioJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Bloqueo> buscar(ConsultorioId consultorio, BloqueoId id) {
        return jdbc.sql("""
                SELECT id, inicio, fin, motivo FROM bloqueo WHERE consultorio_id = :consultorio AND id = :id""")
                .param("consultorio", consultorio.valor())
                .param("id", id.valor())
                .query((fila, numero) -> bloqueo(fila))
                .optional();
    }

    @Override
    public List<Bloqueo> listarQueIntersectan(ConsultorioId consultorio, Intervalo intervalo) {
        return jdbc.sql("""
                SELECT id, inicio, fin, motivo
                  FROM bloqueo
                 WHERE consultorio_id = :consultorio
                   AND tstzrange(inicio, fin, '[)') && tstzrange(:inicio, :fin, '[)')
                 ORDER BY inicio""")
                .param("consultorio", consultorio.valor())
                .param("inicio", Timestamp.from(intervalo.inicio()))
                .param("fin", Timestamp.from(intervalo.fin()))
                .query((fila, numero) -> bloqueo(fila))
                .list();
    }

    @Override
    public void insertar(ConsultorioId consultorio, Bloqueo bloqueo) {
        jdbc.sql("""
                INSERT INTO bloqueo (id, consultorio_id, inicio, fin, motivo)
                VALUES (:id, :consultorio, :inicio, :fin, :motivo)""")
                .param("id", bloqueo.id().valor())
                .param("consultorio", consultorio.valor())
                .param("inicio", Timestamp.from(bloqueo.intervalo().inicio()))
                .param("fin", Timestamp.from(bloqueo.intervalo().fin()))
                .param("motivo", bloqueo.motivo())
                .update();
    }

    @Override
    public boolean eliminar(ConsultorioId consultorio, BloqueoId id) {
        return jdbc.sql("DELETE FROM bloqueo WHERE consultorio_id = :consultorio AND id = :id")
                .param("consultorio", consultorio.valor())
                .param("id", id.valor())
                .update() == 1;
    }

    private static Bloqueo bloqueo(ResultSet fila) throws SQLException {
        return new Bloqueo(new BloqueoId(fila.getObject("id", UUID.class)),
                new Intervalo(TurnoRepositorioJdbc.instante(fila, "inicio"), TurnoRepositorioJdbc.instante(fila, "fin")),
                fila.getString("motivo"));
    }
}
