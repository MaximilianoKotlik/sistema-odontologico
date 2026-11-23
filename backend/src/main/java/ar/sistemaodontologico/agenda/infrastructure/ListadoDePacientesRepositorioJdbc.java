package ar.sistemaodontologico.agenda.infrastructure;

import ar.sistemaodontologico.agenda.application.ListadoDePacientesRepositorio;
import ar.sistemaodontologico.agenda.application.PacienteDeLaLista;
import ar.sistemaodontologico.agenda.application.PacienteDeLaLista.LoInformado;
import ar.sistemaodontologico.agenda.application.PacienteDeLaLista.TurnoDeOrigen;
import ar.sistemaodontologico.agenda.application.PaginaDePacientes;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * La lista y el detalle de pacientes con JdbcClient (C-17, design D4). El orden por apellido, nombre e id es el
 * mismo del buscador de C-12 y no repite ni saltea pacientes entre páginas. El turno de origen se une por
 * consultorio, igual que la FK de DD-38.
 */
@Repository
class ListadoDePacientesRepositorioJdbc implements ListadoDePacientesRepositorio {

    private static final String SELECT = """
            SELECT p.id, p.nombre, p.apellido, p.dni, p.telefono_e164, p.email, p.requiere_revision,
                   p.nombre_informado, p.apellido_informado, p.email_informado, p.informado_at,
                   t.id AS turno_id, t.inicio AS turno_inicio, tp.nombre AS turno_prestacion
              FROM paciente p
              LEFT JOIN turno t ON t.consultorio_id = p.consultorio_id AND t.id = p.informado_turno_id
              LEFT JOIN tipo_prestacion tp ON tp.consultorio_id = t.consultorio_id AND tp.id = t.tipo_prestacion_id
            """;

    private final JdbcClient jdbc;

    ListadoDePacientesRepositorioJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PaginaDePacientes pagina(ConsultorioId consultorio, boolean soloConRevision, int desde, int cantidad) {
        List<PacienteDeLaLista> items = jdbc.sql(SELECT + """
                 WHERE p.consultorio_id = ? AND (NOT ? OR p.requiere_revision)
                 ORDER BY lower(p.apellido), lower(p.nombre), p.id
                 LIMIT ? OFFSET ?""")
                .params(consultorio.valor(), soloConRevision, cantidad, desde)
                .query((fila, numero) -> paciente(fila))
                .list();
        long total = jdbc.sql("""
                SELECT count(*) FROM paciente WHERE consultorio_id = ? AND (NOT ? OR requiere_revision)""")
                .params(consultorio.valor(), soloConRevision)
                .query(Long.class)
                .single();
        return new PaginaDePacientes(items, total);
    }

    @Override
    public Optional<PacienteDeLaLista> buscar(ConsultorioId consultorio, UUID id) {
        return jdbc.sql(SELECT + " WHERE p.consultorio_id = ? AND p.id = ?")
                .params(consultorio.valor(), id)
                .query((fila, numero) -> paciente(fila))
                .optional();
    }

    private static PacienteDeLaLista paciente(ResultSet fila) throws SQLException {
        LoInformado informado = null;
        if (fila.getBoolean("requiere_revision")) {
            UUID turnoId = fila.getObject("turno_id", UUID.class);
            TurnoDeOrigen turno = turnoId == null ? null
                    : new TurnoDeOrigen(turnoId, instante(fila, "turno_inicio"), fila.getString("turno_prestacion"));
            informado = new LoInformado(fila.getString("nombre_informado"), fila.getString("apellido_informado"),
                    fila.getString("email_informado"), instante(fila, "informado_at"), turno);
        }
        return new PacienteDeLaLista(fila.getObject("id", UUID.class), fila.getString("nombre"),
                fila.getString("apellido"), fila.getString("dni"), fila.getString("telefono_e164"),
                fila.getString("email"), fila.getBoolean("requiere_revision"), informado);
    }

    private static java.time.Instant instante(ResultSet fila, String columna) throws SQLException {
        Timestamp valor = fila.getTimestamp(columna);
        return valor == null ? null : valor.toInstant();
    }
}
