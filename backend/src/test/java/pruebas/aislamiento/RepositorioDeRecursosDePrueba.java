package pruebas.aislamiento;

import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Solo de test: repositorio de ejemplo que sigue la convención de tenant (todo método recibe
 * {@link ConsultorioId}). Usa JdbcTemplate y no JPA para no registrar entidades en otros contextos.
 */
public class RepositorioDeRecursosDePrueba {

    public record Recurso(UUID id, UUID consultorioId, String nombre) {
    }

    private final JdbcTemplate jdbc;

    public RepositorioDeRecursosDePrueba(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void guardar(ConsultorioId consultorio, UUID id, String nombre) {
        jdbc.update("INSERT INTO recurso_prueba (id, consultorio_id, nombre) VALUES (?, ?, ?)",
                id, consultorio.valor(), nombre);
    }

    public Optional<Recurso> buscar(ConsultorioId consultorio, UUID id) {
        return jdbc.query("SELECT id, consultorio_id, nombre FROM recurso_prueba WHERE consultorio_id = ? AND id = ?",
                        (fila, n) -> new Recurso(fila.getObject(1, UUID.class), fila.getObject(2, UUID.class), fila.getString(3)),
                        consultorio.valor(), id)
                .stream().findFirst();
    }

    public List<Recurso> listar(ConsultorioId consultorio) {
        return jdbc.query("SELECT id, consultorio_id, nombre FROM recurso_prueba WHERE consultorio_id = ? ORDER BY id",
                (fila, n) -> new Recurso(fila.getObject(1, UUID.class), fila.getObject(2, UUID.class), fila.getString(3)),
                consultorio.valor());
    }
}
