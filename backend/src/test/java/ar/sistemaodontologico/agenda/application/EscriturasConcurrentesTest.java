package ar.sistemaodontologico.agenda.application;

import static org.assertj.core.api.Assertions.assertThat;
import static pruebas.agenda.AgendaDePrueba.lunesA;
import static pruebas.agenda.Errores.assertCodigo;

import ar.sistemaodontologico.agenda.domain.Intervalo;
import ar.sistemaodontologico.agenda.domain.PacienteId;
import ar.sistemaodontologico.compartido.domain.error.ErrorDeNegocio;
import ar.sistemaodontologico.consultorio.application.DisponibilidadRepositorio;
import ar.sistemaodontologico.consultorio.domain.Franja;
import ar.sistemaodontologico.consultorio.domain.GrillaSemanal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Escrituras de agenda concurrentes del mismo consultorio (C-11, tareas 4.6 y 5.2; spec gestion-de-turnos
 * "Escrituras de agenda serializadas por consultorio", design D3). Las dos operaciones arrancan a la vez; el lock de
 * agenda hace que la segunda valide contra lo que dejó la primera.
 */
class EscriturasConcurrentesTest extends CasoDeUsoDeAgendaTest {

    @Autowired
    private CrearTurno crear;

    @Autowired
    private CrearBloqueo bloquear;

    @Autowired
    private DisponibilidadRepositorio disponibilidad;

    @Autowired
    private TurnoRepositorio turnos;

    @Autowired
    private BloqueoRepositorio bloqueos;

    @Autowired
    private PlatformTransactionManager transacciones;

    private UUID consulta;
    private PacienteId ana;

    @BeforeEach
    void prestacionYPaciente() {
        consulta = agenda.prestacion("Consulta", 30, true);
        ana = new PacienteId(agenda.paciente("Ana", "Prueba"));
    }

    @Test
    void dosTurnosAlMismoHorarioAlaVez() throws Exception {
        List<Object> resultados = aLaVez(
                () -> crear.crear(diezYMedia(), odontologo),
                () -> crear.crear(diezYMedia(), odontologo));

        assertThat(resultados).filteredOn(TurnoGuardado.class::isInstance).hasSize(1);
        assertThat(resultados).filteredOn(String.class::isInstance).containsExactly("TURNO_SOLAPADO");
    }

    @Test
    void unTurnoYUnBloqueoALaVezNoSePisan() throws Exception {
        List<Object> resultados = aLaVez(
                () -> crear.crear(diezYMedia(), odontologo),
                () -> bloquear.crear(lunesA("09:00"), lunesA("12:00"), null, odontologo));

        assertThat(resultados).filteredOn(String.class::isInstance).hasSize(1);
        Intervalo manana = new Intervalo(lunesA("09:00"), lunesA("12:00"));
        boolean hayTurno = !turnos.listarActivosQueIntersectan(consultorio, manana).isEmpty();
        boolean hayBloqueo = !bloqueos.listarQueIntersectan(consultorio, manana).isEmpty();
        assertThat(hayTurno).isNotEqualTo(hayBloqueo);
    }

    @Test
    void unTurnoMientrasSeReemplazaLaGrillaSeValidaContraLaNueva() throws Exception {
        // Una grilla sin el lunes: martes a viernes de 09:00 a 13:00.
        List<Franja> sinLunes = new ArrayList<>();
        for (int dia = 2; dia <= 5; dia++) {
            sinLunes.add(new Franja(dia, LocalTime.of(9, 0), LocalTime.of(13, 0)));
        }
        TransactionTemplate transaccion = new TransactionTemplate(transacciones);
        CountDownLatch grillaReemplazada = new CountDownLatch(1);

        // La grilla se reemplaza primero y tarda en confirmar. Sin el lock, el turno leería la grilla vieja (que
        // todavía tiene el lunes) y quedaría creado fuera de la que queda vigente.
        CompletableFuture<Void> grilla = CompletableFuture.runAsync(() -> transaccion.executeWithoutResult(estado -> {
            disponibilidad.reemplazar(consultorio, new GrillaSemanal(sinLunes));
            grillaReemplazada.countDown();
            dormir(1_000);
        }));
        esperar(grillaReemplazada);

        assertCodigo(() -> crear.crear(diezYMedia(), odontologo), "FUERA_DE_DISPONIBILIDAD");
        grilla.get(30, TimeUnit.SECONDS);
        assertThat(turnos.listarActivosQueIntersectan(consultorio, new Intervalo(lunesA("10:00"), lunesA("10:30"))))
                .isEmpty();
    }

    private static void dormir(long milisegundos) {
        try {
            Thread.sleep(milisegundos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private NuevoTurno diezYMedia() {
        return new NuevoTurno(ana, consulta, lunesA("10:00"), Optional.empty(), false);
    }

    /**
     * Corre las dos operaciones en hilos distintos, largadas a la vez. Cada resultado es lo que devolvió la operación
     * o el código del error de negocio con que terminó.
     */
    private static List<Object> aLaVez(Supplier<Object> una, Supplier<Object> otra) throws Exception {
        CountDownLatch largada = new CountDownLatch(1);
        List<CompletableFuture<Object>> futuros = new ArrayList<>();
        for (Supplier<Object> operacion : List.of(una, otra)) {
            futuros.add(CompletableFuture.supplyAsync(() -> {
                esperar(largada);
                try {
                    return operacion.get();
                } catch (ErrorDeNegocio error) {
                    return error.getCodigo();
                }
            }));
        }
        largada.countDown();
        List<Object> resultados = new ArrayList<>();
        for (CompletableFuture<Object> futuro : futuros) {
            try {
                resultados.add(futuro.get(30, TimeUnit.SECONDS));
            } catch (CompletionException e) {
                throw new AssertionError(e.getCause());
            }
        }
        return resultados;
    }

    private static void esperar(CountDownLatch senal) {
        try {
            if (!senal.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("No se largó");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
