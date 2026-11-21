package ar.sistemaodontologico.consultorio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.sistemaodontologico.compartido.IntegracionPostgresTest;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import ar.sistemaodontologico.consultorio.application.DisponibilidadRepositorio;
import ar.sistemaodontologico.consultorio.domain.Franja;
import ar.sistemaodontologico.consultorio.domain.GrillaSemanal;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pruebas.seguridad.CuentasDePrueba;

/**
 * Dos reemplazos concurrentes de la grilla que, cada uno por su lado, son válidos pero se solapan entre sí (C-08,
 * tarea 5.3; design D5). La validación de la aplicación no ve la otra transacción: la frena la exclusión de la base,
 * y el manejador la traduce a 400 {@code FRANJAS_SOLAPADAS} (ManejadorGlobalDeErroresTest).
 */
class DisponibilidadConcurrenteTest extends IntegracionPostgresTest {

    @Autowired
    private DisponibilidadRepositorio disponibilidad;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transacciones;

    @Test
    void laBaseFrenaDosGuardadosConcurrentesQueSeSolapan() throws Exception {
        ConsultorioId consultorio = new ConsultorioId(CuentasDePrueba.crear(jdbc).consultorio());
        TransactionTemplate transaccion = new TransactionTemplate(transacciones);
        CountDownLatch primeroEscribio = new CountDownLatch(1);
        CountDownLatch segundoEmpezo = new CountDownLatch(1);

        CompletableFuture<Void> primero = CompletableFuture.runAsync(() -> transaccion.executeWithoutResult(estado -> {
            disponibilidad.reemplazar(consultorio, grilla(1, "09:00", "13:00"));
            primeroEscribio.countDown();
            esperar(segundoEmpezo);
            // Margen para que el segundo haga su DELETE (que no ve esta fila sin confirmar) y quede esperando en el
            // INSERT. Si confirmara antes, el DELETE del segundo la borraría y no habría choque.
            dormir(1_000);
        }));
        esperar(primeroEscribio);
        CompletableFuture<Void> segundo = CompletableFuture.runAsync(() -> transaccion.executeWithoutResult(estado -> {
            segundoEmpezo.countDown();
            // La exclusión espera a que el primero confirme y entonces rechaza esta fila.
            disponibilidad.reemplazar(consultorio, grilla(1, "12:00", "15:00"));
        }));

        primero.get(30, TimeUnit.SECONDS);
        assertThatThrownBy(() -> segundo.get(30, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ex_disponibilidad_sin_solape");
        assertThat(disponibilidad.consultar(consultorio)).isEqualTo(grilla(1, "09:00", "13:00"));
    }

    private static GrillaSemanal grilla(int dia, String inicio, String fin) {
        return new GrillaSemanal(List.of(new Franja(dia, LocalTime.parse(inicio), LocalTime.parse(fin))));
    }

    private static void dormir(long milisegundos) {
        try {
            Thread.sleep(milisegundos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
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
