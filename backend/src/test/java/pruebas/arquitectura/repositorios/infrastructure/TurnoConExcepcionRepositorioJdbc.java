package pruebas.arquitectura.repositorios.infrastructure;

import java.time.LocalDate;
import java.util.Optional;
import pruebas.arquitectura.repositorios.application.TurnoConExcepcionRepositorio;

/** Fixture: la implementación hereda la excepción del puerto por el prefijo de su nombre. */
public class TurnoConExcepcionRepositorioJdbc implements TurnoConExcepcionRepositorio {

    @Override
    public Optional<String> buscarPorCodigo(String codigo) {
        return Optional.empty();
    }

    @Override
    public Optional<String> buscarPorFecha(LocalDate fecha) {
        return Optional.empty();
    }
}
