package pruebas.arquitectura.repositorios.application;

import java.util.List;

/**
 * Fixture: su nombre empieza igual que el exceptuado ConsultorioRepositorio, pero no lo implementa. La excepción
 * no lo cubre: listar sin consultorio tiene que marcarse.
 */
public interface ConsultorioRepositorioDePrestaciones {

    List<String> listarTodas();
}
