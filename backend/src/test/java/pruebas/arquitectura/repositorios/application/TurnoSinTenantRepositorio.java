package pruebas.arquitectura.repositorios.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Fixture que viola la regla: busca y lista sin consultorio. */
public interface TurnoSinTenantRepositorio {

    Optional<String> buscarPorId(UUID id);

    List<String> listarTodos();
}
