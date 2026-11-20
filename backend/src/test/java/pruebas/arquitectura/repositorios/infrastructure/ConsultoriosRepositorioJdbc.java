package pruebas.arquitectura.repositorios.infrastructure;

import java.util.Optional;
import pruebas.arquitectura.repositorios.application.ConsultorioRepositorio;

/** Fixture: implementa el repositorio exceptuado aunque su nombre no empieza igual; la excepción lo cubre. */
public class ConsultoriosRepositorioJdbc implements ConsultorioRepositorio {

    @Override
    public Optional<String> buscarPorSlug(String slug) {
        return Optional.empty();
    }
}
