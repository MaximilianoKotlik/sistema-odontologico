package ar.sistemaodontologico.reserva.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import ar.sistemaodontologico.agenda.application.Autor;
import ar.sistemaodontologico.agenda.application.ReprogramarTurno;
import ar.sistemaodontologico.agenda.domain.TurnoId;
import ar.sistemaodontologico.compartido.application.TenantContext;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import pruebas.agenda.AgendaDePrueba;
import pruebas.seguridad.TokensDePrueba;

/** Carreras de la API pública contra PostgreSQL real (C-15, tareas 6.5, 6.5a y 7.6; RN-RP-07, RN-GL-02). */
class ConcurrenciaPublicaTest extends ApiPublicaTest {

    private static final int REPETICIONES = 20;

    @Autowired
    private ReprogramarTurno reprogramarTurno;

    @Autowired
    private TenantContext tenant;

    @RepeatedTest(REPETICIONES)
    void dosPacientesAlMismoHueco() throws Exception {
        Instant diez = lunesA("10:00");

        List<ResultActions> resultados = aLaVez(
                () -> reservar(slug, pedido(diez, AgendaDePrueba.dniNuevo())),
                () -> reservar(slug, pedido(diez, AgendaDePrueba.dniNuevo())));

        assertThat(estados(resultados)).containsExactlyInAnyOrder(201, 409);
        ResultActions rechazo = resultados.stream().filter(r -> r.andReturn().getResponse().getStatus() == 409).findFirst()
                .orElseThrow();
        JsonNode error = cuerpo(rechazo);
        assertThat(error.path("codigo").asText()).isEqualTo("HUECO_NO_DISPONIBLE");
        assertThat(huecosDe(error)).doesNotContain(diez.toString());
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM turno WHERE consultorio_id = ? AND estado = 'RESERVADO'""", Long.class,
                agenda.consultorio())).isEqualTo(1);
    }

    @RepeatedTest(REPETICIONES)
    void mismoDniEnDosHorariosConElLimiteAlBorde() throws Exception {
        String dni = AgendaDePrueba.dniNuevo();
        reservar(slug, pedido(lunesA("09:00"), dni));

        List<ResultActions> resultados = aLaVez(
                () -> reservar(slug, pedido(lunesA("10:00"), dni)),
                () -> reservar(slug, pedido(lunesA("11:00"), dni)));

        assertThat(estados(resultados)).containsExactlyInAnyOrder(201, 409);
        ResultActions rechazo = resultados.stream().filter(r -> r.andReturn().getResponse().getStatus() == 409).findFirst()
                .orElseThrow();
        assertThat(cuerpo(rechazo).path("codigo").asText()).isEqualTo("LIMITE_TURNOS");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM paciente WHERE dni = ? AND consultorio_id = ?", Long.class,
                dni, agenda.consultorio())).isEqualTo(1);
    }

    @RepeatedTest(REPETICIONES)
    void reservaContraUnCambioDeGrillaQueQuitaLaFranja() throws Exception {
        Instant diez = lunesA("10:00");
        String acceso = TokensDePrueba.valido(SECRETO_JWT_DE_PRUEBA, agenda.usuario(), agenda.consultorio(), 1);
        String sinLunes = json.writeValueAsString(Map.of("franjas",
                List.of(Map.of("diaSemana", 2, "horaInicio", "09:00", "horaFin", "13:00"))));

        List<ResultActions> resultados = aLaVez(
                () -> reservar(slug, pedido(diez, AgendaDePrueba.dniNuevo())),
                () -> mvc.perform(put("/api/v1/disponibilidad").header(HttpHeaders.AUTHORIZATION, "Bearer " + acceso)
                        .contentType(MediaType.APPLICATION_JSON).content(sinLunes)));

        int reserva = resultados.get(0).andReturn().getResponse().getStatus();
        assertThat(resultados.get(1).andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(reserva).isIn(201, 409);
        if (reserva == 409) {
            assertThat(cuerpo(resultados.get(0)).path("codigo").asText()).isEqualTo("HUECO_NO_DISPONIBLE");
        }
        long turnos = jdbc.queryForObject("SELECT count(*) FROM turno WHERE consultorio_id = ?", Long.class,
                agenda.consultorio());
        assertThat(turnos).isEqualTo(reserva == 201 ? 1 : 0);
    }

    @RepeatedTest(REPETICIONES)
    void dosCancelacionesSimultaneas() throws Exception {
        String token = reservarYObtenerToken(lunesA("10:00"));

        List<ResultActions> resultados = aLaVez(() -> cancelar(token), () -> cancelar(token));

        assertThat(estados(resultados)).contains(200).hasSize(2);
        assertThat(estados(resultados).stream().filter(estado -> estado == 200)).hasSize(1);
        assertThat(estados(resultados).stream().filter(estado -> estado != 200)).allMatch(e -> e == 404 || e == 409);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM evento_auditoria WHERE entidad_id = ? AND accion = 'TURNO_ESTADO_CAMBIADO'""",
                Long.class, turnoDe(token))).isEqualTo(1);
    }

    @RepeatedTest(REPETICIONES)
    void cancelacionDelPacienteContraReprogramacionDelOdontologo() throws Exception {
        String token = reservarYObtenerToken(lunesA("10:00"));
        UUID turno = turnoDe(token);
        Autor odontologo = Autor.odontologo(agenda.usuario(), Optional.empty());

        CountDownLatch largada = new CountDownLatch(2);
        CompletableFuture<Integer> paciente = CompletableFuture.supplyAsync(() -> {
            esperar(largada);
            try {
                return cancelar(token).andReturn().getResponse().getStatus();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        CompletableFuture<Boolean> dentista = CompletableFuture.supplyAsync(() -> {
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
            try {
                tenant.establecer(new ConsultorioId(agenda.consultorio()));
                esperar(largada);
                reprogramarTurno.reprogramar(new TurnoId(turno), 0, lunesA("11:00"), odontologo);
                return true;
            } catch (RuntimeException e) {
                return false;
            } finally {
                RequestContextHolder.resetRequestAttributes();
            }
        });

        int cancelacion = paciente.get(30, TimeUnit.SECONDS);
        boolean movido = dentista.get(30, TimeUnit.SECONDS);

        Map<String, Object> fila = jdbc.queryForMap("SELECT estado, inicio, version FROM turno WHERE id = ?", turno);
        assertThat(cancelacion).isIn(200, 409);
        if (cancelacion == 200) {
            assertThat(fila).containsEntry("estado", "CANCELADO");
        } else {
            assertThat(movido).isTrue();
            assertThat(fila).containsEntry("estado", "RESERVADO");
        }
        long cambios = jdbc.queryForObject("""
                SELECT count(*) FROM evento_auditoria
                 WHERE entidad_id = ? AND accion IN ('TURNO_ESTADO_CAMBIADO', 'TURNO_REPROGRAMADO')""", Long.class, turno);
        assertThat(cambios).isEqualTo((cancelacion == 200 ? 1 : 0) + (movido ? 1 : 0));
    }

    private static List<Integer> estados(List<ResultActions> resultados) {
        return resultados.stream().map(r -> r.andReturn().getResponse().getStatus()).toList();
    }

    @SafeVarargs
    private List<ResultActions> aLaVez(Callable<ResultActions>... operaciones) throws Exception {
        CountDownLatch largada = new CountDownLatch(operaciones.length);
        List<CompletableFuture<ResultActions>> futuros = java.util.Arrays.stream(operaciones)
                .map(operacion -> CompletableFuture.supplyAsync(() -> {
                    esperar(largada);
                    try {
                        return operacion.call();
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }))
                .toList();
        CompletableFuture.allOf(futuros.toArray(CompletableFuture[]::new)).get(30, TimeUnit.SECONDS);
        return futuros.stream().map(CompletableFuture::join).toList();
    }

    private static void esperar(CountDownLatch largada) {
        largada.countDown();
        try {
            largada.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
