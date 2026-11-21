package ar.sistemaodontologico.consultorio.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import ar.sistemaodontologico.compartido.IntegracionPostgresTest;
import ar.sistemaodontologico.compartido.application.PublicadorDeAuditoria;
import ar.sistemaodontologico.compartido.application.TenantContext;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import ar.sistemaodontologico.compartido.domain.error.RecursoNoEncontrado;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import pruebas.agenda.AgendaDePrueba;
import pruebas.reloj.ConfiguracionDeRelojDePrueba;
import pruebas.reloj.RelojDePrueba;

/**
 * Vencimiento diario y pase manual a {@code ACTIVO} (C-20, tareas 2.1 y 2.3; design D2 y D3; spec plan-del-consultorio).
 * Corren sin petición HTTP, como la tarea programada y la operación interna. Usa la misma configuración que
 * {@code CasoDeUsoDeAgendaTest} para compartir el contexto. Datos ficticios (regla dura 17).
 */
@Import(ConfiguracionDeRelojDePrueba.class)
class CicloDeVidaDelPlanTest extends IntegracionPostgresTest {

    private static final ZoneId BUENOS_AIRES = ZoneId.of("America/Argentina/Buenos_Aires");

    record Evento(String accion, String actorTipo, UUID actorId, String entidad, UUID entidadId, String anterior,
            String nuevo) {
    }

    @MockitoBean
    TenantContext tenant;

    @MockitoSpyBean
    PublicadorDeAuditoria auditoria;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RelojDePrueba reloj;

    @Autowired
    VencerPruebas vencimiento;

    @Autowired
    ActivarPlan activacion;

    private UUID consultorio;

    @BeforeEach
    void crearConsultorio() {
        consultorio = AgendaDePrueba.nueva(jdbc).consultorio();
    }

    @Nested
    class Vencimiento {

        @Test
        void unaPruebaVencidaPasaAVencidoYQuedaAuditada() {
            plan(consultorio, "PRUEBA", hoy().minusDays(1));

            vencimiento.vencer(reloj.instant());

            assertThat(planDe(consultorio)).isEqualTo("VENCIDO");
            assertThat(eventos(consultorio)).containsExactly(new Evento("PLAN_VENCIDO", "SISTEMA", null, "consultorio",
                    consultorio, "{\"plan\": \"PRUEBA\"}", "{\"plan\": \"VENCIDO\"}"));
        }

        @Test
        void correrlaOtraVezNoDuplicaElEvento() {
            plan(consultorio, "PRUEBA", hoy().minusDays(1));

            vencimiento.vencer(reloj.instant());
            vencimiento.vencer(reloj.instant());

            assertThat(planDe(consultorio)).isEqualTo("VENCIDO");
            assertThat(acciones(consultorio)).containsExactly("PLAN_VENCIDO");
        }

        @Test
        void dosCorridasALaVezDejanUnSoloEvento() throws Exception {
            plan(consultorio, "PRUEBA", hoy().minusDays(1));
            CountDownLatch largada = new CountDownLatch(1);
            ExecutorService hilos = Executors.newFixedThreadPool(2);
            try {
                List<CompletableFuture<Void>> corridas = List.of(1, 2).stream()
                        .map(numero -> CompletableFuture.runAsync(() -> {
                            esperar(largada);
                            vencimiento.vencer(reloj.instant());
                        }, hilos))
                        .toList();
                largada.countDown();
                CompletableFuture.allOf(corridas.toArray(CompletableFuture[]::new)).get();
            } finally {
                hilos.shutdownNow();
            }

            assertThat(acciones(consultorio)).containsExactly("PLAN_VENCIDO");
        }

        @Test
        void noTocaPruebasVigentesNiPlanesActivos() {
            UUID activo = AgendaDePrueba.nueva(jdbc).consultorio();
            plan(consultorio, "PRUEBA", hoy());
            plan(activo, "ACTIVO", hoy().minusMonths(1));

            vencimiento.vencer(reloj.instant());

            assertThat(planDe(consultorio)).isEqualTo("PRUEBA");
            assertThat(planDe(activo)).isEqualTo("ACTIVO");
            assertThat(acciones(consultorio)).isEmpty();
            assertThat(acciones(activo)).isEmpty();
        }

        @Test
        void laFechaSeCuentaEnLaZonaDelConsultorio() {
            // 01:00 UTC: en Madrid ya es el día siguiente a prueba_hasta; en Buenos Aires todavía no.
            Instant ahora = reloj.instant().atZone(BUENOS_AIRES).toLocalDate().plusDays(1).atStartOfDay(BUENOS_AIRES)
                    .toInstant().minus(Duration.ofHours(2));
            LocalDate pruebaHasta = ahora.atZone(BUENOS_AIRES).toLocalDate();
            UUID enMadrid = AgendaDePrueba.nueva(jdbc).consultorio();
            jdbc.update("UPDATE consultorio SET zona_horaria = 'Europe/Madrid' WHERE id = ?", enMadrid);
            plan(consultorio, "PRUEBA", pruebaHasta);
            plan(enMadrid, "PRUEBA", pruebaHasta);

            vencimiento.vencer(ahora);

            assertThat(planDe(consultorio)).isEqualTo("PRUEBA");
            assertThat(planDe(enMadrid)).isEqualTo("VENCIDO");
        }

        @Test
        void siFallaLaAuditoriaElPlanNoCambia() {
            plan(consultorio, "PRUEBA", hoy().minusDays(1));
            doThrow(new IllegalStateException("auditoría caída")).when(auditoria).publicar(any());

            assertThatThrownBy(() -> vencimiento.vencer(reloj.instant())).isInstanceOf(IllegalStateException.class);

            assertThat(planDe(consultorio)).isEqualTo("PRUEBA");
            assertThat(acciones(consultorio)).isEmpty();
        }
    }

    @Test
    void enLosTestsLaTareaNoEstaProgramada(@Autowired ScheduledAnnotationBeanPostProcessor programadas) {
        assertThat(programadas.getScheduledTasks()).isEmpty();
    }

    @Nested
    class Activacion {

        @Test
        void activarUnConsultorioVencido() {
            plan(consultorio, "VENCIDO", hoy().minusDays(5));

            activacion.activar(new ConsultorioId(consultorio));

            assertThat(planDe(consultorio)).isEqualTo("ACTIVO");
            assertThat(eventos(consultorio)).containsExactly(new Evento("PLAN_ACTIVADO", "SISTEMA", null,
                    "consultorio", consultorio, "{\"plan\": \"VENCIDO\"}", "{\"plan\": \"ACTIVO\"}"));
        }

        @Test
        void activarUnaPrueba() {
            plan(consultorio, "PRUEBA", hoy().plusDays(10));

            activacion.activar(new ConsultorioId(consultorio));

            assertThat(planDe(consultorio)).isEqualTo("ACTIVO");
            assertThat(eventos(consultorio)).extracting(Evento::anterior).containsExactly("{\"plan\": \"PRUEBA\"}");
        }

        @Test
        void yaActivoNoRegistraOtroEvento() {
            plan(consultorio, "ACTIVO", hoy().minusDays(5));

            activacion.activar(new ConsultorioId(consultorio));

            assertThat(planDe(consultorio)).isEqualTo("ACTIVO");
            assertThat(acciones(consultorio)).isEmpty();
        }

        @Test
        void unConsultorioInexistenteFalla() {
            assertThatThrownBy(() -> activacion.activar(new ConsultorioId(UUID.randomUUID())))
                    .isInstanceOf(RecursoNoEncontrado.class);
        }
    }

    private LocalDate hoy() {
        return reloj.instant().atZone(BUENOS_AIRES).toLocalDate();
    }

    private void plan(UUID id, String plan, LocalDate pruebaHasta) {
        jdbc.update("UPDATE consultorio SET plan = ?, prueba_hasta = ? WHERE id = ?", plan, pruebaHasta, id);
    }

    private String planDe(UUID id) {
        return jdbc.queryForObject("SELECT plan FROM consultorio WHERE id = ?", String.class, id);
    }

    private List<Evento> eventos(UUID id) {
        return jdbc.query("""
                SELECT accion, actor_tipo, actor_id, entidad, entidad_id, valor_anterior::text AS anterior,
                       valor_nuevo::text AS nuevo
                  FROM evento_auditoria WHERE consultorio_id = ? ORDER BY id""",
                (fila, numero) -> new Evento(fila.getString("accion"), fila.getString("actor_tipo"),
                        fila.getObject("actor_id", UUID.class), fila.getString("entidad"),
                        fila.getObject("entidad_id", UUID.class), fila.getString("anterior"), fila.getString("nuevo")),
                id);
    }

    private List<String> acciones(UUID id) {
        return eventos(id).stream().map(Evento::accion).toList();
    }

    private static void esperar(CountDownLatch largada) {
        try {
            largada.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
