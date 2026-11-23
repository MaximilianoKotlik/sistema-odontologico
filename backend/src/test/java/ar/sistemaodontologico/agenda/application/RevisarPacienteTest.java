package ar.sistemaodontologico.agenda.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static pruebas.agenda.Errores.assertCodigo;

import ar.sistemaodontologico.agenda.domain.OrigenPaciente;
import ar.sistemaodontologico.agenda.domain.PacienteId;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import pruebas.agenda.AgendaDePrueba;

/**
 * Confirmar y corregir la revisión de un paciente (C-17, tareas 3.1 a 3.4; spec revision-pacientes, design D5, D6 y
 * D9). Persistencia real con Testcontainers (regla dura 8). Datos ficticios (regla dura 17).
 */
class RevisarPacienteTest extends CasoDeUsoDeAgendaTest {

    private static final Instant RESERVA = Instant.parse("2026-11-20T13:00:00.123456Z");
    private static final String DNI = "90000011";

    @Autowired
    private RevisarPaciente revisar;

    @Autowired
    private RegistrarPaciente registrar;

    private PacienteId ana;

    @BeforeEach
    void anaMarcadaComoAnna() {
        UUID id = agenda.paciente("Ana", "Ejemplo", DNI, "+5491100000011", "ana@example.com");
        agenda.informado(id, "Anna", "Ejemplo", "anna@example.com", RESERVA, null);
        ana = new PacienteId(id);
    }

    @Test
    void confirmarMantieneLosDatosYBorraLoInformado() {
        revisar.confirmar(ana, RESERVA, odontologo);

        assertThat(fila()).containsEntry("nombre", "Ana").containsEntry("apellido", "Ejemplo")
                .containsEntry("email", "ana@example.com").containsEntry("requiere_revision", false)
                .containsEntry("nombre_informado", null).containsEntry("apellido_informado", null)
                .containsEntry("email_informado", null).containsEntry("informado_at", null);
        Evento evento = eventos().getLast();
        assertThat(evento.accion()).isEqualTo("PACIENTE_REVISADO");
        assertThat(evento.actorTipo()).isEqualTo("ODONTOLOGO");
        assertThat(evento.actorId()).isEqualTo(agenda.usuario());
        assertThat(evento.entidad()).isEqualTo("paciente");
        assertThat(evento.entidadId()).isEqualTo(ana.valor());
        assertThat(evento.anterior()).isEqualTo("{\"requiere_revision\": true}");
        assertThat(evento.nuevo()).isEqualTo("{\"requiere_revision\": false}");
        assertThat(evento.ip()).isEqualTo("203.0.113.10");
    }

    @Test
    void confirmarDosVecesEsSinRevisionPendiente() {
        revisar.confirmar(ana, RESERVA, odontologo);

        assertCodigo(() -> revisar.confirmar(ana, RESERVA, odontologo), "SIN_REVISION_PENDIENTE");
        // D5: se evalúa antes que el informadoAt.
        assertCodigo(() -> revisar.confirmar(ana, RESERVA.plusSeconds(1), odontologo), "SIN_REVISION_PENDIENTE");
        assertCodigo(() -> revisar.corregir(ana, RESERVA, datos("Anna", "Ejemplo", null), odontologo),
                "SIN_REVISION_PENDIENTE");
        assertThat(acciones()).containsOnlyOnce("PACIENTE_REVISADO");
    }

    @Test
    void corregirConLoDeLaReservaAuditaSoloLoQueCambio() {
        revisar.corregir(ana, RESERVA, datos("Anna", "Ejemplo", "ana@example.com"), odontologo);

        assertThat(fila()).containsEntry("nombre", "Anna").containsEntry("apellido", "Ejemplo")
                .containsEntry("requiere_revision", false).containsEntry("nombre_informado", null)
                .containsEntry("informado_at", null);
        Evento evento = eventos().getLast();
        assertThat(evento.accion()).isEqualTo("PACIENTE_REVISADO");
        assertThat(evento.anterior()).isEqualTo("{\"nombre\": \"Ana\", \"requiere_revision\": true}");
        assertThat(evento.nuevo()).isEqualTo("{\"nombre\": \"Anna\", \"requiere_revision\": false}");
    }

    @Test
    void corregirVariosCamposYQuitarElEmail() {
        revisar.corregir(ana, RESERVA, datos("  Anna ", "Otro", "  "), odontologo);

        assertThat(fila()).containsEntry("nombre", "Anna").containsEntry("apellido", "Otro")
                .containsEntry("email", null);
        Evento evento = eventos().getLast();
        assertThat(evento.anterior()).isEqualTo(
                "{\"email\": \"ana@example.com\", \"nombre\": \"Ana\", \"apellido\": \"Ejemplo\", \"requiere_revision\": true}");
        assertThat(evento.nuevo()).isEqualTo(
                "{\"email\": null, \"nombre\": \"Anna\", \"apellido\": \"Otro\", \"requiere_revision\": false}");
    }

    @Test
    void unDatoInvalidoNoCambiaNada() {
        for (DatosCorregidos invalidos : new DatosCorregidos[] {datos(" ", "Ejemplo", null),
                datos("Anna", null, null), datos("A".repeat(81), "Ejemplo", null),
                datos("Anna", "Ejemplo", "no-es-un-email")}) {
            assertCodigo(() -> revisar.corregir(ana, RESERVA, invalidos, odontologo), "VALIDACION");
        }

        assertThat(fila()).containsEntry("nombre", "Ana").containsEntry("requiere_revision", true)
                .containsEntry("nombre_informado", "Anna");
        assertThat(acciones()).doesNotContain("PACIENTE_REVISADO");
    }

    @Test
    void sinInformadoAtEsValidacion() {
        assertCodigo(() -> revisar.confirmar(ana, null, odontologo), "VALIDACION");
        assertCodigo(() -> revisar.corregir(ana, null, datos("Anna", "Ejemplo", null), odontologo), "VALIDACION");
    }

    @Test
    void unInformadoAtDistintoEsConflictoDeConcurrencia() {
        assertThatThrownBy(() -> revisar.confirmar(ana, RESERVA.plusNanos(1000), odontologo))
                .isInstanceOf(OptimisticLockingFailureException.class);

        assertThat(fila()).containsEntry("requiere_revision", true).containsEntry("nombre_informado", "Anna");
        assertThat(acciones()).doesNotContain("PACIENTE_REVISADO");
    }

    @Test
    void unaReservaQuePisaLoInformadoEntreElDetalleYLaRevisionNoSePierde() {
        Instant visto = RESERVA;
        registrar.registrar(new DatosDePaciente("Annita", "Ejemplo", DNI, "+5491100000011", null),
                OrigenPaciente.MANUAL, null, odontologo);

        assertThatThrownBy(() -> revisar.confirmar(ana, visto, odontologo))
                .isInstanceOf(OptimisticLockingFailureException.class);
        assertThatThrownBy(() -> revisar.corregir(ana, visto, datos("Anna", "Ejemplo", null), odontologo))
                .isInstanceOf(OptimisticLockingFailureException.class);

        assertThat(fila()).containsEntry("nombre", "Ana").containsEntry("requiere_revision", true)
                .containsEntry("nombre_informado", "Annita");
        assertThat(acciones()).doesNotContain("PACIENTE_REVISADO");
    }

    @Test
    void siLaAuditoriaFallaNoCambiaNada() {
        doThrow(new IllegalStateException("falla simulada")).when(auditoria).publicar(any());

        assertThatThrownBy(() -> revisar.corregir(ana, RESERVA, datos("Anna", "Ejemplo", null), odontologo))
                .isInstanceOf(IllegalStateException.class);

        assertThat(fila()).containsEntry("nombre", "Ana").containsEntry("requiere_revision", true)
                .containsEntry("nombre_informado", "Anna");
    }

    @Test
    void unPacienteInexistenteODeOtroConsultorioNoSeEncuentra() {
        UUID ajena = AgendaDePrueba.nueva(jdbc).paciente("Beatriz", "Ajena");

        for (UUID id : new UUID[] {UUID.randomUUID(), ajena}) {
            assertCodigo(() -> revisar.confirmar(new PacienteId(id), RESERVA, odontologo), "RECURSO_NO_ENCONTRADO");
            assertCodigo(() -> revisar.corregir(new PacienteId(id), RESERVA, datos("Anna", "Ejemplo", null),
                    odontologo), "RECURSO_NO_ENCONTRADO");
        }
    }

    private static DatosCorregidos datos(String nombre, String apellido, String email) {
        return new DatosCorregidos(nombre, apellido, email);
    }

    private Map<String, Object> fila() {
        return jdbc.queryForMap("""
                SELECT nombre, apellido, email::text AS email, requiere_revision, nombre_informado, apellido_informado,
                       email_informado::text AS email_informado, informado_at
                  FROM paciente WHERE id = ?""", ana.valor());
    }
}
