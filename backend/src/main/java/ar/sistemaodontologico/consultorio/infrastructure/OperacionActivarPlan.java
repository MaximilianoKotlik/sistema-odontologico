package ar.sistemaodontologico.consultorio.infrastructure;

import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import ar.sistemaodontologico.consultorio.application.ActivarPlan;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Operación interna del equipo para pasar un consultorio a {@code ACTIVO} (C-20, RN-PL-04, design D3). Solo existe
 * con el perfil {@code operacion}, que arranca sin servidor web; no hay ninguna ruta HTTP que cambie el plan. Se usa
 * así (runbook en {@code backend/README.md}):
 *
 * <pre>--spring.profiles.active=operacion --operacion.consultorio=&lt;uuid&gt;</pre>
 */
@Component
@Profile("operacion")
class OperacionActivarPlan implements ApplicationRunner {

    static final String OPCION = "operacion.consultorio";

    private static final Logger LOG = LoggerFactory.getLogger(OperacionActivarPlan.class);

    private final ActivarPlan activacion;

    OperacionActivarPlan(ActivarPlan activacion) {
        this.activacion = activacion;
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        ConsultorioId consultorio = consultorioPedido(argumentos);
        Optional<String> anterior = activacion.activar(consultorio);
        if (anterior.isPresent()) {
            LOG.info("Consultorio {} activado (antes: {})", consultorio.valor(), anterior.get());
        } else {
            LOG.info("Consultorio {} ya estaba activo: sin cambios", consultorio.valor());
        }
    }

    private static ConsultorioId consultorioPedido(ApplicationArguments argumentos) {
        List<String> valores = argumentos.getOptionValues(OPCION);
        if (valores == null || valores.size() != 1) {
            throw new IllegalArgumentException("Falta --" + OPCION + "=<uuid> (un solo consultorio por corrida)");
        }
        return new ConsultorioId(UUID.fromString(valores.get(0).trim()));
    }
}
