package ar.sistemaodontologico.agenda.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import pruebas.agenda.AgendaDePrueba;

/**
 * {@code GET /api/v1/pacientes/listado} y {@code GET /api/v1/pacientes/{id}} (C-17, tareas 2.1 a 2.3; spec
 * revision-pacientes, design D4 y D10). Datos ficticios (regla dura 17).
 */
class ListadoDePacientesTest extends ApiDeAgendaTest {

    private static final Instant RESERVA = Instant.parse("2026-11-20T13:00:00Z");

    @Test
    void filtraLosMarcadosOrdenadosPorApellido() throws Exception {
        UUID zeta = agenda.paciente("Ana", "Zeta");
        agenda.paciente("Bruno", "Prueba");
        UUID alfa = agenda.paciente("Carla", "Alfa");
        agenda.paciente("Dario", "Ficticio");
        UUID medio = agenda.paciente("Eva", "Medio");
        agenda.paciente("Fede", "Ejemplo");
        for (UUID marcado : new UUID[] {zeta, alfa, medio}) {
            agenda.informado(marcado, "Otro", "Nombre", null, RESERVA, null);
        }

        listado("?requiereRevision=true")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", contains(alfa.toString(), medio.toString(), zeta.toString())))
                .andExpect(jsonPath("$.items[*].requiereRevision", contains(true, true, true)))
                .andExpect(jsonPath("$.total").value(3));
    }

    @Test
    void sinElFiltroDevuelveTodosPorApellidoYNombre() throws Exception {
        UUID bruno = agenda.paciente("Bruno", "Prueba", "90000001", "+5491100000001", "bruno@example.com");
        UUID ana = agenda.paciente("Ana", "Prueba");
        UUID carla = agenda.paciente("Carla", "Ficticia");
        agenda.informado(ana, "Anna", "Prueba", null, RESERVA, null);

        listado("")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", contains(carla.toString(), ana.toString(), bruno.toString())))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items[2].nombre").value("Bruno"))
                .andExpect(jsonPath("$.items[2].apellido").value("Prueba"))
                .andExpect(jsonPath("$.items[2].dni").value("90000001"))
                .andExpect(jsonPath("$.items[2].telefono").value("+5491100000001"))
                .andExpect(jsonPath("$.items[2].email").value("bruno@example.com"))
                .andExpect(jsonPath("$.items[2].requiereRevision").value(false));
        listado("?requiereRevision=false").andExpect(jsonPath("$.total").value(3));
    }

    @Test
    void paginaDeA20SinRepetirNiSaltear() throws Exception {
        for (int i = 0; i < 30; i++) {
            agenda.paciente("Paciente", "Repetida");
        }

        JsonNode primera = cuerpo(listado("").andExpect(jsonPath("$.items", hasSize(20)))
                .andExpect(jsonPath("$.total").value(30)));
        JsonNode segunda = cuerpo(listado("?page=1").andExpect(jsonPath("$.items", hasSize(10)))
                .andExpect(jsonPath("$.total").value(30)));

        Set<String> ids = new HashSet<>();
        primera.get("items").forEach(item -> ids.add(item.get("id").asText()));
        segunda.get("items").forEach(item -> ids.add(item.get("id").asText()));
        assertThat(ids).hasSize(30);
        listado("?page=2&size=7").andExpect(jsonPath("$.items", hasSize(7)));
        listado("?size=50").andExpect(jsonPath("$.items", hasSize(30)));
    }

    @Test
    void paginadoInvalido() throws Exception {
        for (String parametros : new String[] {"?size=51", "?size=0", "?page=-1"}) {
            listado(parametros).andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDACION"));
        }
    }

    @Test
    void loInformadoSoloEnLosMarcados() throws Exception {
        UUID consulta = agenda.prestacion("Consulta", 30, true);
        UUID ana = agenda.paciente("Ana", "Ejemplo", "90000002", "+5491100000002", "ana@example.com");
        UUID turno = agenda.turno(ana, consulta, AgendaDePrueba.lunesA("10:30"), 30, "RESERVADO", false);
        agenda.informado(ana, "Anna", "Ejemplo", "anna@example.com", RESERVA, turno);
        agenda.paciente("Bruno", "Prueba");

        listado("")
                .andExpect(jsonPath("$.items[0].informado.nombre").value("Anna"))
                .andExpect(jsonPath("$.items[0].informado.apellido").value("Ejemplo"))
                .andExpect(jsonPath("$.items[0].informado.email").value("anna@example.com"))
                .andExpect(jsonPath("$.items[0].informado.informadoAt").value("2026-11-20T13:00:00Z"))
                .andExpect(jsonPath("$.items[0].informado.turno.id").value(turno.toString()))
                .andExpect(jsonPath("$.items[0].informado.turno.inicio").value("2026-11-23T13:30:00Z"))
                .andExpect(jsonPath("$.items[0].informado.turno.prestacion").value("Consulta"))
                .andExpect(jsonPath("$.items[1].informado").doesNotExist());
    }

    @Test
    void elListadoNoMuestraPacientesDeOtroConsultorio() throws Exception {
        UUID propia = agenda.paciente("Ana", "Propia");
        AgendaDePrueba deB = otraAgenda();
        UUID ajena = deB.paciente("Beatriz", "Ajena");
        deB.informado(ajena, "Bea", "Ajena", null, RESERVA, null);

        listado("").andExpect(jsonPath("$.items[*].id", contains(propia.toString())))
                .andExpect(jsonPath("$.total").value(1));
        listado("?requiereRevision=true").andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void detalleDeUnPacienteMarcado() throws Exception {
        UUID consulta = agenda.prestacion("Consulta", 30, true);
        UUID ana = agenda.paciente("Ana", "Ejemplo", "90000003", "+5491100000003", null);
        UUID turno = agenda.turno(ana, consulta, AgendaDePrueba.lunesA("10:30"), 30, "RESERVADO", false);
        agenda.informado(ana, "Anna", "Ejemplo", null, RESERVA, turno);

        detalle(ana)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ana.toString()))
                .andExpect(jsonPath("$.nombre").value("Ana"))
                .andExpect(jsonPath("$.dni").value("90000003"))
                .andExpect(jsonPath("$.requiereRevision").value(true))
                .andExpect(jsonPath("$.informado.nombre").value("Anna"))
                .andExpect(jsonPath("$.informado.email").doesNotExist())
                .andExpect(jsonPath("$.informado.informadoAt").value("2026-11-20T13:00:00Z"))
                .andExpect(jsonPath("$.informado.turno.id").value(turno.toString()));
    }

    @Test
    void detalleDeUnPacienteSinRevision() throws Exception {
        UUID bruno = agenda.paciente("Bruno", "Prueba", "90000004", "+5491100000004", "bruno@example.com");

        detalle(bruno)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("bruno@example.com"))
                .andExpect(jsonPath("$.requiereRevision").value(false))
                .andExpect(jsonPath("$.informado").doesNotExist());
    }

    @Test
    void detalleInexistenteODeOtroConsultorioEs404() throws Exception {
        UUID ajena = otraAgenda().paciente("Beatriz", "Ajena");

        for (UUID id : new UUID[] {UUID.randomUUID(), ajena}) {
            detalle(id).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
        }
    }

    private ResultActions listado(String parametros) throws Exception {
        return mvc.perform(conToken(get("/api/v1/pacientes/listado" + parametros), token));
    }

    private ResultActions detalle(UUID id) throws Exception {
        return mvc.perform(conToken(get("/api/v1/pacientes/" + id), token));
    }
}
