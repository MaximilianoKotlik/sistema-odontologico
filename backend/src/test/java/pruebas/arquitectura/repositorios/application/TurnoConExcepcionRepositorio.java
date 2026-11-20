package pruebas.arquitectura.repositorios.application;

import java.time.LocalDate;
import java.util.Optional;

/** Fixture: una excepción documentada por método no cubre a los otros métodos de la misma clase. */
public interface TurnoConExcepcionRepositorio {

    Optional<String> buscarPorCodigo(String codigo);

    Optional<String> buscarPorFecha(LocalDate fecha);
}
