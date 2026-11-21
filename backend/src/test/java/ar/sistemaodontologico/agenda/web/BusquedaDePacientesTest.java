package ar.sistemaodontologico.agenda.web;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import pruebas.agenda.AgendaDePrueba;

/** {@code GET /api/v1/pacientes?q=} (C-12, tarea 4.1; spec busqueda-de-pacientes, design D7; C-14 D4). */
class BusquedaDePacientesTest extends ApiDeAgendaTest {

    private UUID ana;
    private UUID carla;

    @BeforeEach
    void cargarPacientes() {
        ana = agenda.paciente("Ana", "Prueba", "90000001", "+5491100000001", "ana@example.com");
        carla = agenda.paciente("Carla", "Ficticia", "91234567", "+5491100000052", null);
    }

    @Test
    void porApellido() throws Exception {
        buscar("fict")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(carla.toString())))
                .andExpect(jsonPath("$[0].nombre").value("Carla"))
                .andExpect(jsonPath("$[0].apellido").value("Ficticia"))
                .andExpect(jsonPath("$[0].dni").value("91234567"))
                .andExpect(jsonPath("$[0].telefono").value("+5491100000052"))
                .andExpect(jsonPath("$[0].requiereRevision").value(false));
    }

    @Test
    void porNombreSinDistinguirMayusculasYConVariasPalabras() throws Exception {
        buscar("ANA").andExpect(jsonPath("$[*].id", contains(ana.toString())));
        buscar("carla prueba").andExpect(jsonPath("$[*].id", containsInAnyOrder(ana.toString(), carla.toString())));
    }

    @Test
    void sinDistinguirTildes() throws Exception {
        UUID jose = agenda.paciente("José", "Pérez");

        buscar("perez").andExpect(jsonPath("$[*].id", contains(jose.toString())));
        buscar("PÉREZ").andExpect(jsonPath("$[*].id", contains(jose.toString())));
        buscar("jose").andExpect(jsonPath("$[*].id", contains(jose.toString())));
        buscar("Ánà").andExpect(jsonPath("$[*].id", contains(ana.toString())));
    }

    @Test
    void elNombreTieneQueEmpezarConLaPalabra() throws Exception {
        buscar("rueba").andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void porDniConPuntos() throws Exception {
        buscar("90.000").andExpect(jsonPath("$[*].id", contains(ana.toString())));
        buscar("912").andExpect(jsonPath("$[*].id", contains(carla.toString())));
    }

    @Test
    void porTelefono() throws Exception {
        buscar("0000001").andExpect(jsonPath("$[*].id", contains(ana.toString())));
        buscar("+54 9 11 0000-0052").andExpect(jsonPath("$[*].id", contains(carla.toString())));
    }

    @Test
    void textoDemasiadoCorto() throws Exception {
        buscar("an").andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDACION"));
        buscar("  an  ").andExpect(status().isBadRequest());
        buscar("9.0").andExpect(status().isBadRequest());
    }

    @Test
    void losComodinesNoSonComodines() throws Exception {
        buscar("%%%").andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        buscar("___").andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void comoMucho20() throws Exception {
        for (int i = 0; i < 25; i++) {
            agenda.paciente("Paciente" + i, "Repetida");
        }

        buscar("repetida").andExpect(jsonPath("$", hasSize(20)));
    }

    @Test
    void pacienteDeOtroConsultorio() throws Exception {
        AgendaDePrueba deB = otraAgenda();
        deB.paciente("Beatriz", "Ajena");

        buscar("ajena").andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    private ResultActions buscar(String q) throws Exception {
        return mvc.perform(conToken(get("/api/v1/pacientes").param("q", q), token));
    }
}
