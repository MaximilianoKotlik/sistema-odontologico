package ar.sistemaodontologico.consultorio.infrastructure;

import ar.sistemaodontologico.consultorio.application.VencerPruebas;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tarea diaria de vencimiento de las pruebas (C-20, FL-07, design D2). El cron se ajusta con
 * {@code app.plan.vencimiento.cron}; con {@code -} queda desactivada (perfiles {@code test} y {@code operacion}).
 * Si no corre, el bloqueo igual empieza a tiempo: lo decide la fecha (design D1).
 */
@Component
class TareaDeVencimiento {

    private static final Logger LOG = LoggerFactory.getLogger(TareaDeVencimiento.class);

    private final VencerPruebas vencimiento;
    private final Clock reloj;

    TareaDeVencimiento(VencerPruebas vencimiento, Clock reloj) {
        this.vencimiento = vencimiento;
        this.reloj = reloj;
    }

    @Scheduled(cron = "${app.plan.vencimiento.cron:0 0 3 * * *}", zone = "America/Argentina/Buenos_Aires")
    void correr() {
        int vencidos = vencimiento.vencer(reloj.instant()).size();
        LOG.info("Vencimiento de pruebas: {} consultorios pasaron a VENCIDO", vencidos);
    }
}
