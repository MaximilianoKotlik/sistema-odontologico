package ar.sistemaodontologico.reserva.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.sistemaodontologico.auditoria.application.LecturaDeAuditoria;
import ar.sistemaodontologico.auditoria.application.LecturaDeAuditoria.CriteriosDeRegistro;
import ar.sistemaodontologico.auditoria.application.LecturaDeAuditoria.EventoLeido;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pruebas.agenda.AgendaDePrueba;

/**
 * El consentimiento y la marca de revisión de una reserva con un DNI ya registrado y otro nombre no se atribuyen a la
 * persona de la ficha (issue #66, Ley 25.326). Pasa por la reserva real para cubrir el orden de sus eventos.
 */
class IdentidadEnRevisionDelPacienteTest extends ApiPublicaTest {

    private static final String CONSENTIMIENTO = "PACIENTE_CONSENTIMIENTO_ACTUALIZADO";
    private static final String MARCADO = "PACIENTE_MARCADO_PARA_REVISION";
    private static final String NOMBRE_DE_LA_FICHA = "Paciente Prueba";

    @Autowired
    private LecturaDeAuditoria lectura;

    private String dni;

    @BeforeEach
    void pacienteExistente() throws Exception {
        dni = AgendaDePrueba.dniNuevo();
        reservar(slug, pedido(lunesA("09:00"), dni)).andExpect(status().isCreated());
        reloj.avanzar(Duration.ofMinutes(1));
    }

    @Test
    void conOtroNombreLosDosEventosNoTraenElNombreDeLaFicha() throws Exception {
        reservarConOtroNombre("10:00");

        assertThat(eventos(MARCADO)).hasSize(1).allSatisfy(IdentidadEnRevisionDelPacienteTest::sinNombre);
        assertThat(eventos(CONSENTIMIENTO)).hasSize(1).allSatisfy(IdentidadEnRevisionDelPacienteTest::sinNombre);
    }

    @Test
    void conElNombreIgualQuedanComoHoy() throws Exception {
        reservar(slug, pedido(lunesA("10:00"), dni)).andExpect(status().isCreated());

        assertThat(eventos(MARCADO)).isEmpty();
        assertThat(eventos(CONSENTIMIENTO)).hasSize(1).allSatisfy(IdentidadEnRevisionDelPacienteTest::conNombre);
    }

    @Test
    void elConsentimientoAnteriorALaReservaDudosaConservaElNombre() throws Exception {
        reservar(slug, pedido(lunesA("10:00"), dni)).andExpect(status().isCreated());
        reloj.avanzar(Duration.ofMinutes(1));
        liberarCupoDeLink();
        reservarConOtroNombre("11:00");

        List<EventoLeido> consentimientos = eventos(CONSENTIMIENTO);

        assertThat(consentimientos).hasSize(2);
        sinNombre(consentimientos.get(0));
        conNombre(consentimientos.get(1));
    }

    @Test
    void elConsentimientoPosteriorConElNombreCorrectoConservaElNombre() throws Exception {
        reservarConOtroNombre("10:00");
        reloj.avanzar(Duration.ofMinutes(1));
        liberarCupoDeLink();
        reservar(slug, pedido(lunesA("11:00"), dni)).andExpect(status().isCreated());

        List<EventoLeido> consentimientos = eventos(CONSENTIMIENTO);

        assertThat(consentimientos).hasSize(2);
        conNombre(consentimientos.get(0));
        sinNombre(consentimientos.get(1));
    }

    @Test
    void alResolverLaRevisionVuelveElNombreDeLaFicha() throws Exception {
        reservarConOtroNombre("10:00");
        jdbc.update("""
                UPDATE paciente SET requiere_revision = false, nombre_informado = NULL, apellido_informado = NULL,
                       email_informado = NULL, informado_at = NULL, informado_turno_id = NULL
                 WHERE dni = ? AND consultorio_id = ?""", dni, agenda.consultorio());

        assertThat(eventos(MARCADO)).hasSize(1).allSatisfy(IdentidadEnRevisionDelPacienteTest::conNombre);
        assertThat(eventos(CONSENTIMIENTO)).hasSize(1).allSatisfy(IdentidadEnRevisionDelPacienteTest::conNombre);
    }

    /** La reserva admite 2 turnos futuros por link (RN-RP-08): estos casos necesitan una tercera. */
    private void liberarCupoDeLink() {
        jdbc.update("UPDATE turno SET origen = 'MANUAL' WHERE consultorio_id = ?", agenda.consultorio());
    }

    private void reservarConOtroNombre(String hora) throws Exception {
        Map<String, Object> otro = pedido(lunesA(hora), dni);
        otro.put("nombre", "Otra");
        otro.put("apellido", "Persona");
        reservar(slug, otro).andExpect(status().isCreated());
    }

    /** Del más nuevo al más viejo, como el registro. */
    private List<EventoLeido> eventos(String accion) {
        return lectura.listarRegistro(new ConsultorioId(agenda.consultorio()),
                new CriteriosDeRegistro(null, null, null, List.of(accion), null, null, 10));
    }

    private static void sinNombre(EventoLeido evento) {
        assertThat(evento.actor().identidadEnRevision()).isTrue();
        assertThat(evento.actor().nombre()).isNull();
    }

    private static void conNombre(EventoLeido evento) {
        assertThat(evento.actor().identidadEnRevision()).isFalse();
        assertThat(evento.actor().nombre()).isEqualTo(NOMBRE_DE_LA_FICHA);
    }
}
