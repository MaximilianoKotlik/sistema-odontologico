package ar.sistemaodontologico.consultorio.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pruebas.agenda.AgendaDePrueba.lunesA;

import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import ar.sistemaodontologico.consultorio.application.ActivarPlan;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.ResultActions;
import pruebas.seguridad.ClienteDeAuth;

/**
 * Solo lectura con el plan vencido (C-20, tareas 3.1 y 3.3; design D4; spec solo-lectura-por-plan). Datos ficticios
 * (regla dura 17).
 */
class SoloLecturaPorPlanTest extends ApiDelPlanTest {

    private static final String DETALLE = "El plan del consultorio está vencido: la agenda está en solo lectura";

    private UUID consulta;
    private UUID ana;

    @BeforeEach
    void prepararAgenda() {
        consulta = agenda.prestacion("Consulta", 30, true);
        ana = agenda.paciente("Ana", "Prueba");
    }

    @Nested
    class EscriturasBloqueadas {

        @Test
        void crearUnTurnoConElPlanVencido() throws Exception {
            plan(agenda, "VENCIDO", hoy().minusDays(2));

            crearTurno()
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.codigo").value("PLAN_VENCIDO"))
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.detail").value(DETALLE));

            assertThat(jdbc.queryForObject("SELECT count(*) FROM turno WHERE consultorio_id = ?", Long.class,
                    consultorio())).isZero();
            assertThat(accionesDelConsultorio(agenda)).isEmpty();
        }

        @Test
        void crearUnBloqueoConLaPruebaVencidaAntesDeLaTarea() throws Exception {
            pruebaVencidaAyer(agenda);

            pedir(post("/api/v1/bloqueos").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("inicio", lunesA("12:00"), "fin", lunesA("13:00")))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.codigo").value("PLAN_VENCIDO"));

            assertThat(jdbc.queryForObject("SELECT count(*) FROM bloqueo WHERE consultorio_id = ?", Long.class,
                    consultorio())).isZero();
        }

        @Test
        void elDetalleNoTraeDatosDelConsultorio() throws Exception {
            plan(agenda, "VENCIDO", hoy().minusDays(2));

            String cuerpo = crearTurno().andReturn().getResponse().getContentAsString();

            assertThat(cuerpo).doesNotContain(consultorio().toString(), ana.toString(), "Consultorio Demo");
        }
    }

    @Nested
    class PlanVigente {

        @Test
        void conElPlanActivoSeCreaElTurno() throws Exception {
            plan(agenda, "ACTIVO", hoy().minusDays(40));

            crearTurno().andExpect(status().isCreated());
        }

        @Test
        void conLaPruebaVigenteSeCreaElTurno() throws Exception {
            plan(agenda, "PRUEBA", hoy());

            crearTurno().andExpect(status().isCreated());
        }

        @Test
        void alActivarloVuelveAAceptarEscrituras(@Autowired ActivarPlan activacion) throws Exception {
            plan(agenda, "VENCIDO", hoy().minusDays(2));
            crearTurno().andExpect(status().isForbidden());

            activacion.activar(new ConsultorioId(consultorio()));

            crearTurno().andExpect(status().isCreated());
        }
    }

    @Nested
    class RutasPermitidas {

        @BeforeEach
        void vencer() {
            plan(agenda, "VENCIDO", hoy().minusDays(2));
        }

        @Test
        void leerLaAgenda() throws Exception {
            pedir(get("/api/v1/agenda?desde=" + lunesA("00:00") + "&hasta=" + lunesA("23:00")))
                    .andExpect(status().isOk());
            pedir(get("/api/v1/consultorio")).andExpect(status().isOk());
        }

        @Test
        void laExportacionNoRespondePlanVencido() throws Exception {
            // C-19 todavía no está en main: hoy responde 404; cuando llegue, la lista permitida ya la incluye.
            String cuerpo = pedir(post("/api/v1/exportacion").contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andReturn().getResponse().getContentAsString();

            assertThat(cuerpo).doesNotContain("PLAN_VENCIDO");
        }

        @Test
        void marcarUnaNovedadComoVista() throws Exception {
            Instant novedad = Instant.parse("2026-11-21T13:00:00Z");
            UUID turno = agenda.turno(ana, consulta, lunesA("10:00"), 30, "CANCELADO", false);
            agenda.novedad(turno, novedad);

            pedir(post("/api/v1/turnos/" + turno + "/visto").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("novedadAt", novedad.toString()))))
                    .andExpect(status().isNoContent());
        }

        @Test
        void sinSesionSigueSiendo401() throws Exception {
            mvc.perform(post("/api/v1/turnos").contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class SesionYContrasena {

        @Autowired
        TestRestTemplate http;

        @Test
        void cerrarSesionEntrarYCambiarLaContrasena() {
            ClienteDeAuth cliente = new ClienteDeAuth(http, ORIGEN_FRONTEND);
            String email = cliente.registrar("plan-vencido");
            jdbc.update("""
                    UPDATE consultorio SET plan = 'VENCIDO'
                     WHERE id = (SELECT consultorio_id FROM usuario WHERE email = ?)""", email);

            ResponseEntity<String> login = cliente.login(email, ClienteDeAuth.CONTRASENA);
            ResponseEntity<String> refrescado = cliente.refrescar(ClienteDeAuth.refreshDe(login));
            ResponseEntity<String> logout = cliente.postConSesion("/api/v1/auth/logout", null, accessDe(refrescado),
                    ClienteDeAuth.refreshDe(refrescado));
            ResponseEntity<String> otraVez = cliente.login(email, ClienteDeAuth.CONTRASENA);
            ResponseEntity<String> cambio = cliente.postConSesion("/api/v1/auth/cambiar-password",
                    Map.of("passwordActual", ClienteDeAuth.CONTRASENA, "passwordNueva", "otra-clave-muy-larga"),
                    accessDe(otraVez), ClienteDeAuth.refreshDe(otraVez));

            assertThat(java.util.List.of(login, refrescado, logout, otraVez, cambio)).allSatisfy(respuesta -> {
                assertThat(respuesta.getStatusCode().is2xxSuccessful()).as(respuesta.toString()).isTrue();
                assertThat(String.valueOf(respuesta.getBody())).doesNotContain("PLAN_VENCIDO");
            });
        }

        private String accessDe(ResponseEntity<String> login) {
            try {
                return json.readTree(login.getBody()).path("accessToken").asText();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private ResultActions crearTurno() throws Exception {
        return pedir(post("/api/v1/turnos").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("pacienteId", ana, "prestacionId", consulta, "inicio",
                        lunesA("10:00"), "esSobreturno", false))));
    }
}
