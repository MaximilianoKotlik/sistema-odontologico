package pruebas.arquitectura.repositorios.application;

import java.util.Optional;

/** Fixture exceptuado: la raíz del tenant se busca por su slug (C-02, design D9). */
public interface ConsultorioRepositorio {

    Optional<String> buscarPorSlug(String slug);
}
