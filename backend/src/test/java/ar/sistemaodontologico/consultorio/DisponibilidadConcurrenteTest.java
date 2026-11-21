package ar.sistemaodontologico.consultorio;

import static org.assertj.core.api.Assertions.assertThat;

import ar.sistemaodontologico.compartido.IntegracionPostgresTest;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import ar.sistemaodontologico.consultorio.application.DisponibilidadRepositorio;
import ar.sistemaodontologico.consultorio.domain.Franja;
import ar.sistemaodontologico.consultorio.domain.GrillaSemanal;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pruebas.seguridad.CuentasDePrueba;

/**
 * Dos reemplazos concurrentes de la grilla del mismo consultorio (C-08, tarea 5.3; design D5; revisión de Maxi en
 * #15). Sin un bloqueo, el {@code DELETE} de cada transacción no ve las filas que la otra todavía no confirmó: dos
 * grillas que no se solapan quedaban sumadas. Con el bloqueo de la fila del consultorio, el segundo espera al primero
 * y lo reemplaza, como si hubieran llegado uno después del otro.
 */
class DisponibilidadConcurrenteTest extends IntegracionPostgresTest {

    @Autowired
    private DisponibilidadRepositorio disponibilidad;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transacciones;

    @Test
    void dosGrillasQueNoSeSolapanNoQuedanSumadas() throws Exception {
        ConsultorioId consultorio = new ConsultorioId(CuentasDePrueba.crear(jdbc).consultorio());

        reemplazarALaVez(consultorio, grilla(1, "09:00", "13:00"), grilla(2, "09:00", "13:00"));

        assertThat(disponibilidad.consultar(consultorio)).isEqualTo(grilla(2, "09:00", "13:00"));
    }

    @Test
    void dosGrillasQueSeSolapanTerminanEnLaSegunda() throws Exception {
        ConsultorioId consultorio = new ConsultorioId(CuentasDePrueba.crear(jdbc).consultorio());

        // Antes del bloqueo, el segundo chocaba con la exclusión de la base; ahora espera y reemplaza.
        reemplazarALaVez(consultorio, grilla(1, "09:00", "13:00"), grilla(1, "12:00", "15:00"));

        assertThat(disponibilidad.consultar(consultorio)).isEqualTo(grilla(1, "12:00", "15:00"));
    }

    /**
     * El primero reemplaza y no confirma hasta que el segundo ya empezó su transacción. El margen antes del commit le
     * da tiempo al segundo de llegar a su {@code DELETE}: sin bloqueo, ese {@code DELETE} no ve la fila sin confirmar.
     */
    private void reemplazarALaVez(ConsultorioId consultorio, GrillaSemanal primera, GrillaSemanal segunda)
            throws Exception {
        TransactionTemplate transaccion = new TransactionTemplate(transacciones);
        CountDownLatch primeroEscribio = new CountDownLatch(1);
        CountDownLatch segundoEmpezo = new CountDownLatch(1);

        CompletableFuture<Void> primero = CompletableFuture.runAsync(() -> transaccion.executeWithoutResult(estado -> {
            disponibilidad.reemplazar(consultorio, primera);
            primeroEscribio.countDown();
            esperar(segundoEmpezo);
            dormir(1_000);
        }));
        esperar(primeroEscribio);
        CompletableFuture<Void> segundo = CompletableFuture.runAsync(() -> transaccion.executeWithoutResult(estado -> {
            segundoEmpezo.countDown();
            disponibilidad.reemplazar(consultorio, segunda);
        }));

        primero.get(30, TimeUnit.SECONDS);
        segundo.get(30, TimeUnit.SECONDS);
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
