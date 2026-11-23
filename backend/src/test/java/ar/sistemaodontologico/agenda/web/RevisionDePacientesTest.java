package ar.sistemaodontologico.agenda.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * {@code POST /api/v1/pacientes/{id}/confirmar-revision} y {@code PATCH /api/v1/pacientes/{id}} (C-17, tareas 3.1 a
 * 3.3; spec revision-pacientes, design D5 y D9). Datos ficticios (regla dura 17).
 */
@ExtendWith(OutputCaptureExtension.class)
class RevisionDePacientesTest extends ApiDeAgendaTest {

    private static final String RESERVA = "2026-11-20T13:00:00.123456Z";

    private UUID ana;

    @BeforeEach
    void anaMarcadaComoAnna() {
        ana = agenda.paciente("Ana", "Ejemplo", "90000021", "+5491100000021", null);
        agenda.informado(ana, "Anna", "Ejemplo", null, Instant.parse(RESERVA), null);
    }

    @Test
    void confirmarDevuelveElPacienteRevisado() throws Exception {
        confirmar(ana, "{\"informadoAt\":\"" + RESERVA + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Ana"))
                .andExpect(jsonPath("$.requiereRevision").value(false))
                .andExpect(jsonPath("$.informado").doesNotExist());

        confirmar(ana, "{\"informadoAt\":\"" + RESERVA + "\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SIN_REVISION_PENDIENTE"));
    }

    @Test
    void corregirIgnoraElDniYElTelefono() throws Exception {
        corregir(ana, """
                {"informadoAt":"%s","nombre":"Anna","apellido":"Ejemplo","email":"anna@example.com",
                 "dni":"99999999","telefono":"+5491100000099"}""".formatted(RESERVA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Anna"))
                .andExpect(jsonPath("$.email").value("anna@example.com"))
                .andExpect(jsonPath("$.dni").value("90000021"))
                .andExpect(jsonPath("$.telefono").value("+5491100000021"))
                .andExpect(jsonPath("$.requiereRevision").value(false));

        Map<String, Object> fila = jdbc.queryForMap("SELECT dni, telefono_e164 FROM paciente WHERE id = ?", ana);
        assertThat(fila).containsEntry("dni", "90000021").containsEntry("telefono_e164", "+5491100000021");
    }

    @Test
    void datosInvalidosOSinInformadoAtSon400() throws Exception {
        corregir(ana, "{\"informadoAt\":\"" + RESERVA + "\",\"nombre\":\"\",\"apellido\":\"Ejemplo\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDACION"));
        corregir(ana, "{\"nombre\":\"Anna\",\"apellido\":\"Ejemplo\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDACION"));
        confirmar(ana, "{}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    @Test
    void otraVarianteEsConflictoDeConcurrencia() throws Exception {
        confirmar(ana, "{\"informadoAt\":\"2026-11-20T13:00:00Z\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO_CONCURRENCIA"));
    }

    @Test
    void pacienteDeOtroConsultorioEs404() throws Exception {
        UUID ajena = otraAgenda().paciente("Beatriz", "Ajena");

        confirmar(ajena, "{\"informadoAt\":\"" + RESERVA + "\"}")
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
        corregir(ajena, "{\"informadoAt\":\"" + RESERVA + "\",\"nombre\":\"Bea\",\"apellido\":\"Ajena\"}")
                .andExpect(status().isNotFound());
    }

    /** Tarea 3.5 (RN-GL-01): un error en la corrección no escribe en los logs lo que se mandó. */
    @Test
    void unErrorEnLaCorreccionNoEscribeLosDatosEnLosLogs(CapturedOutput salida) throws Exception {
        corregir(ana, """
                {"informadoAt":"%s","nombre":"NombreMarcadorPii","apellido":"Ejemplo",
                 "email":"marcador-pii-sin-arroba"}""".formatted(RESERVA))
                .andExpect(status().isBadRequest());
        corregir(ana, """
                {"informadoAt":"2026-11-20T13:00:00Z","nombre":"OtroMarcadorPii","apellido":"Ejemplo",
                 "email":"marcador-pii@example.com"}""")
                .andExpect(status().isConflict());

        assertThat(salida.getOut())
                .doesNotContain("NombreMarcadorPii")
                .doesNotContain("marcador-pii-sin-arroba")
                .doesNotContain("OtroMarcadorPii")
                .doesNotContain("marcador-pii@example.com")
                .doesNotContain("Anna");
    }

    private ResultActions confirmar(UUID id, String cuerpo) throws Exception {
        return mvc.perform(conToken(post("/api/v1/pacientes/" + id + "/confirmar-revision")
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo), token));
    }

    private ResultActions corregir(UUID id, String cuerpo) throws Exception {
        return mvc.perform(conToken(patch("/api/v1/pacientes/" + id)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo), token));
    }
}
