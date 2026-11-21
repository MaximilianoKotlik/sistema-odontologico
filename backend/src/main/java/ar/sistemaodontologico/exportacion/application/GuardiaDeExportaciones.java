package ar.sistemaodontologico.exportacion.application;

import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Una exportación en curso por consultorio, dentro de esta instancia (C-19, design D7). Entre instancias coordina el
 * lock consultivo de {@link CandadoDeExportacion}.
 */
@Component
public class GuardiaDeExportaciones {

    private final Set<ConsultorioId> enCurso = ConcurrentHashMap.newKeySet();

    /** Toma la guardia; {@code false} si ese consultorio ya tiene una exportación en curso. */
    public boolean tomar(ConsultorioId consultorio) {
        return enCurso.add(consultorio);
    }

    public void liberar(ConsultorioId consultorio) {
        enCurso.remove(consultorio);
    }

    public boolean estaTomada(ConsultorioId consultorio) {
        return enCurso.contains(consultorio);
    }
}
