package ar.sistemaodontologico.agenda.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ar.sistemaodontologico.compartido.IntegracionPostgresTest;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import ar.sistemaodontologico.consultorio.application.DisponibilidadRepositorio;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pruebas.agenda.AgendaDePrueba;

/**
 * {@link LockDeAgenda} (C-11, tarea 2.1; design D3): la misma fila y el mismo modo que el reemplazo de la grilla de
 * C-08, así las escrituras de agenda y los cambios de disponibilidad de un consultorio quedan en una sola cola.
 */
class LockDeAgendaTest extends IntegracionPostgresTest {

    @Autowired
    private LockDeAgenda lock;

    @Autowired
    private DisponibilidadRepositorio disponibilidad;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transacciones;

    @Test
    void elLockDeAgendaHaceEsperarAlReemplazoDeLaGrilla() throws Exception {
        ConsultorioId consultorio = new ConsultorioId(AgendaDePrueba.nueva(jdbc).consultorio());

        assertThat(segundoEspera(consultorio, lock::tomar, disponibilidad::bloquear)).isTrue();
    }

    @Test
    void elReemplazoDeLaGrillaHaceEsperarAlLockDeAgenda() throws Exception {
        ConsultorioId consultorio = new ConsultorioId(AgendaDePrueba.nueva(jdbc).consultorio());

        assertThat(segundoEspera(consultorio, disponibilidad::bloquear, lock::tomar)).isTrue();
    }

    @Test
    void otroConsultorioNoEspera() throws Exception {
        ConsultorioId uno = new ConsultorioId(AgendaDePrueba.nueva(jdbc).consultorio());
        ConsultorioId otro = new ConsultorioId(AgendaDePrueba.nueva(jdbc).consultorio());

        assertThat(segundoEspera(uno, lock::tomar, ignorado -> lock.tomar(otro))).isFalse();
    }

    @Test
    void repetirloEnLaMismaTransaccionNoEspera() {
        ConsultorioId consultorio = new ConsultorioId(AgendaDePrueba.nueva(jdbc).consultorio());

        assertThatCode(() -> new TransactionTemplate(transacciones).executeWithoutResult(estado -> {
            lock.tomar(consultorio);
            lock.tomar(consultorio);
        })).doesNotThrowAnyException();
    }

    /**
     * El primero toma su lock y lo retiene; el segundo intenta el suyo. Devuelve si el segundo tuvo que esperar a que
     * el primero confirmara.
     */
    private boolean segundoEspera(ConsultorioId consultorio, Consumer<ConsultorioId> primero,
            Consumer<ConsultorioId> segundo) throws Exception {
        TransactionTemplate transaccion = new TransactionTemplate(transacciones);
        CountDownLatch primeroTomo = new CountDownLatch(1);
        CountDownLatch soltar = new CountDownLatch(1);

        CompletableFuture<Void> retiene = CompletableFuture.runAsync(() -> transaccion.executeWithoutResult(estado -> {
            primero.accept(consultorio);
            primeroTomo.countDown();
            esperar(soltar);
        }));
        esperar(primeroTomo);
        CompletableFuture<Void> intenta = CompletableFuture.runAsync(
                () -> transaccion.executeWithoutResult(estado -> segundo.accept(consultorio)));

        boolean espero;
        try {
            intenta.get(500, TimeUnit.MILLISECONDS);
            espero = false;
        } catch (java.util.concurrent.TimeoutException e) {
            espero = true;
        }
        soltar.countDown();
        retiene.get(30, TimeUnit.SECONDS);
        intenta.get(30, TimeUnit.SECONDS);
        return espero;
    }

    private static void esperar(CountDownLatch senal) {
        try {
            if (!senal.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("La otra transacción no avanzó");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
