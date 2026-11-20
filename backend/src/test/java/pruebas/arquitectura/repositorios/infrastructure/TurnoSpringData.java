package pruebas.arquitectura.repositorios.infrastructure;

import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Fixture: interfaz de Spring Data (hereda findById sin tenant si se la expone). */
public interface TurnoSpringData extends Repository<Object, UUID> {
}
