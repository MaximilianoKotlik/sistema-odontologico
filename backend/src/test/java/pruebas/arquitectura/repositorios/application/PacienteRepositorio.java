package pruebas.arquitectura.repositorios.application;

import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Fixture válido: todo método de consulta recibe el consultorio. */
public interface PacienteRepositorio {

    Optional<String> buscar(ConsultorioId consultorio, UUID id);

    List<String> listar(ConsultorioId consultorio);

    boolean existe(ConsultorioId consultorio, String telefono);

    void guardar(String paciente);
}
