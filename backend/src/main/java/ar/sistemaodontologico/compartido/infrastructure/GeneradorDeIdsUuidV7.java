package ar.sistemaodontologico.compartido.infrastructure;

import ar.sistemaodontologico.compartido.domain.GeneradorDeIds;
import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** UUIDv7 con java-uuid-generator (design D3). Monótono dentro del mismo milisegundo. */
@Component
public class GeneradorDeIdsUuidV7 implements GeneradorDeIds {

    private final TimeBasedEpochGenerator generador = Generators.timeBasedEpochGenerator();

    @Override
    public UUID nuevo() {
        return generador.generate();
    }
}
