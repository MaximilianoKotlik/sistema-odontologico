package ar.sistemaodontologico.agenda.application;

import static pruebas.agenda.Errores.assertCodigo;
import static org.assertj.core.api.Assertions.assertThat;
import static pruebas.agenda.AgendaDePrueba.lunesA;

import ar.sistemaodontologico.agenda.domain.Consentimiento;
import ar.sistemaodontologico.agenda.domain.OrigenPaciente;
import ar.sistemaodontologico.agenda.domain.TurnoId;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pruebas.agenda.AgendaDePrueba;

/**
 * {@link RegistrarPaciente} (C-11, tareas 3.1 a 3.4; spec registro-de-pacientes, design D6; DD-24 y DD-38). Datos
 * ficticios (regla dura 17).
 */
class RegistrarPacienteTest extends CasoDeUsoDeAgendaTest {

    private static final Consentimiento CONSENTIMIENTO =
            new Consentimiento(Instant.parse("2026-11-20T12:00:00Z"), "2026-11-borrador");

    @Autowired
    private RegistrarPaciente registrar;

    @Autowired
    private PlatformTransactionManager transacciones;

    @Test
    void unDniNuevoCreaElPacienteYLoAudita() {
        PacienteRegistrado registrado = registrar.registrar(datos("Ana", "Prueba", "90.000.001", null),
                OrigenPaciente.MANUAL, null, odontologo);

        assertThat(registrado.reutilizado()).isFalse();
        assertThat(registrado.marcadoParaRevision()).isFalse();
        assertThat(columnas(registrado.paciente().id().valor()))
                .containsEntry("nombre", "Ana").containsEntry("dni", "90000001")
                .containsEntry("telefono_e164", "+5491100000001").containsEntry("requiere_revision", false);
        Evento evento = eventos().getFirst();
        assertThat(evento.accion()).isEqualTo(RegistrarPaciente.ACCION_CREADO);
        assertThat(evento.entidad()).isEqualTo("paciente");
        assertThat(evento.actorTipo()).isEqualTo("ODONTOLOGO");
        assertThat(evento.nuevo()).isEqualTo("{\"origen\": \"MANUAL\"}");
        assertThat(evento.ip()).isEqualTo("203.0.113.10");
    }

    @Test
    void elPacienteQueReservaPorElLinkQuedaComoActor() {
        PacienteRegistrado registrado = registrar.registrar(datos("Ana", "Prueba", AgendaDePrueba.dniNuevo(), null),
                OrigenPaciente.LINK, CONSENTIMIENTO, Autor.paciente(null, Optional.empty()));

        Evento evento = eventos().getFirst();
        assertThat(evento.actorTipo()).isEqualTo("PACIENTE");
        assertThat(evento.actorId()).isEqualTo(registrado.paciente().id().valor());
        assertThat(columnas(registrado.paciente().id().valor())).containsEntry("origen", "LINK");
    }

    @Test
    void unDniExistenteSeReutilizaSinSobrescribir() {
        PacienteRegistrado primero = registrar.registrar(datos("Ana", "Prueba", "90000001", "ana@example.com"),
                OrigenPaciente.MANUAL, null, odontologo);

        PacienteRegistrado segundo = registrar.registrar(new DatosDePaciente("Ana", "Prueba", "90.000.001",
                "+5491100000099", "otra@example.com"), OrigenPaciente.LINK, CONSENTIMIENTO, odontologo);

        assertThat(segundo.reutilizado()).isTrue();
        assertThat(segundo.marcadoParaRevision()).isFalse();
        assertThat(segundo.paciente().id()).isEqualTo(primero.paciente().id());
        assertThat(segundo.paciente().telefonoE164()).isEqualTo("+5491100000001");
        assertThat(columnas(primero.paciente().id().valor()))
                .containsEntry("telefono_e164", "+5491100000001").containsEntry("email", "ana@example.com")
                .containsEntry("origen", "MANUAL");
        assertThat(acciones()).containsExactly(RegistrarPaciente.ACCION_CREADO);
    }

    @Test
    void primeraMarcaGuardaLoInformado() {
        PacienteRegistrado ana = registrar.registrar(datos("Ana", "Prueba", "90000001", null), OrigenPaciente.MANUAL,
                null, odontologo);

        PacienteRegistrado marcado = registrar.registrar(datos("Ana María", "Prueba", "90000001",
                "Ana.Maria@Example.com"), OrigenPaciente.LINK, CONSENTIMIENTO, odontologo);

        assertThat(marcado.reutilizado()).isTrue();
        assertThat(marcado.marcadoParaRevision()).isTrue();
        assertThat(columnas(ana.paciente().id().valor()))
                .containsEntry("nombre", "Ana").containsEntry("requiere_revision", true)
                .containsEntry("nombre_informado", "Ana María").containsEntry("apellido_informado", "Prueba")
                .containsEntry("email_informado", "ana.maria@example.com")
                .containsEntry("informado_turno_id", null);
        assertThat(jdbc.queryForObject("SELECT informado_at FROM paciente WHERE id = ?", java.sql.Timestamp.class,
                ana.paciente().id().valor()).toInstant())
                .isEqualTo(reloj.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        Evento evento = eventos().getLast();
        assertThat(evento.accion()).isEqualTo(RegistrarPaciente.ACCION_MARCADO);
        assertThat(evento.anterior()).isEqualTo("{\"requiere_revision\": false}");
        assertThat(evento.nuevo()).isEqualTo("{\"requiere_revision\": true}");
    }

    @Test
    void unaSegundaReservaDistintaPisaLoInformado() {
        PacienteRegistrado ana = registrar.registrar(datos("Ana", "Prueba", "90000001", null), OrigenPaciente.MANUAL,
                null, odontologo);
        registrar.registrar(datos("Ana María", "Prueba", "90000001", "ana.maria@example.com"), OrigenPaciente.LINK,
                CONSENTIMIENTO, odontologo);

        PacienteRegistrado otra = registrar.registrar(datos("Anita", "Prueba", "90000001", null), OrigenPaciente.LINK,
                CONSENTIMIENTO, odontologo);

        assertThat(otra.marcadoParaRevision()).isTrue();
        assertThat(columnas(ana.paciente().id().valor()))
                .containsEntry("nombre", "Ana").containsEntry("nombre_informado", "Anita")
                .containsEntry("email_informado", null).containsEntry("requiere_revision", true);
        Evento evento = eventos().getLast();
        assertThat(evento.accion()).isEqualTo(RegistrarPaciente.ACCION_MARCADO);
        assertThat(evento.anterior()).isEqualTo("{\"requiere_revision\": true}");
        assertThat(evento.nuevo()).isEqualTo("{\"requiere_revision\": true}");
    }

    @Test
    void elMismoNombreConAcentosDistintosNoCambiaNada() {
        PacienteRegistrado jose = registrar.registrar(datos("José", "Pérez", "90000001", null), OrigenPaciente.MANUAL,
                null, odontologo);

        PacienteRegistrado otro = registrar.registrar(datos("jose", "perez", "90000001", "jose@example.com"),
                OrigenPaciente.LINK, CONSENTIMIENTO, odontologo);

        assertThat(otro.marcadoParaRevision()).isFalse();
        assertThat(columnas(jose.paciente().id().valor()))
                .containsEntry("requiere_revision", false).containsEntry("nombre_informado", null);
        assertThat(acciones()).containsExactly(RegistrarPaciente.ACCION_CREADO);
    }

    @Test
    void elEventoDeLaMarcaNoLlevaLoInformado() {
        registrar.registrar(datos("Ana", "Prueba", "90000001", null), OrigenPaciente.MANUAL, null, odontologo);
        registrar.registrar(datos("Anna", "Ejemplo", "90000001", "anna@example.com"), OrigenPaciente.LINK,
                CONSENTIMIENTO, odontologo);

        assertThat(eventos()).allSatisfy(evento -> assertThat(evento.anterior() + evento.nuevo())
                .doesNotContain("Ana").doesNotContain("Anna").doesNotContain("Ejemplo").doesNotContain("Prueba")
                .doesNotContain("90000001").doesNotContain("example.com").doesNotContain("+549"));
    }

    @Test
    void vincularElTurnoDeOrigenSoloSiEsteRegistroMarco() {
        UUID prestacion = agenda.prestacion("Consulta", 30, true);
        PacienteRegistrado ana = registrar.registrar(datos("Ana", "Prueba", "90000001", null), OrigenPaciente.MANUAL,
                null, odontologo);
        UUID turno = agenda.turno(ana.paciente().id().valor(), prestacion, lunesA("10:00"), 30, "RESERVADO", false);

        new TransactionTemplate(transacciones).executeWithoutResult(estado -> {
            PacienteRegistrado sinDiferencias = registrar.registrar(datos("Ana", "Prueba", "90000001", null),
                    OrigenPaciente.LINK, CONSENTIMIENTO, odontologo);
            registrar.vincularTurnoDeOrigen(sinDiferencias, new TurnoId(turno));
        });
        assertThat(columnas(ana.paciente().id().valor())).containsEntry("informado_turno_id", null);

        new TransactionTemplate(transacciones).executeWithoutResult(estado -> {
            PacienteRegistrado marcado = registrar.registrar(datos("Anna", "Prueba", "90000001", null),
                    OrigenPaciente.LINK, CONSENTIMIENTO, odontologo);
            registrar.vincularTurnoDeOrigen(marcado, new TurnoId(turno));
        });
        assertThat(columnas(ana.paciente().id().valor())).containsEntry("informado_turno_id", turno);
        assertThat(acciones()).containsExactly(RegistrarPaciente.ACCION_CREADO, RegistrarPaciente.ACCION_MARCADO);
    }

    @Test
    void dosRegistrosSimultaneosDejanUnSoloPaciente() throws Exception {
        String dni = AgendaDePrueba.dniNuevo();

        List<CompletableFuture<PacienteRegistrado>> registros = List.of(
                CompletableFuture.supplyAsync(() -> registrar.registrar(datos("Ana", "Prueba", dni, null),
                        OrigenPaciente.MANUAL, null, odontologo)),
                CompletableFuture.supplyAsync(() -> registrar.registrar(datos("Ana", "Prueba", dni, null),
                        OrigenPaciente.MANUAL, null, odontologo)));

        List<PacienteRegistrado> resultados = List.of(registros.get(0).get(30, TimeUnit.SECONDS),
                registros.get(1).get(30, TimeUnit.SECONDS));
        assertThat(resultados).extracting(PacienteRegistrado::reutilizado).containsExactlyInAnyOrder(true, false);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM paciente WHERE consultorio_id = ? AND dni = ?",
                Integer.class, consultorio.valor(), dni)).isEqualTo(1);
    }

    @Test
    void elMismoDniEnOtroConsultorioEsOtroPaciente() {
        PacienteRegistrado enA = registrar.registrar(datos("Ana", "Prueba", "90000001", null), OrigenPaciente.MANUAL,
                null, odontologo);
        ConsultorioId otro = new ConsultorioId(AgendaDePrueba.nueva(jdbc).consultorio());
        org.mockito.Mockito.when(tenant.actual()).thenReturn(otro);

        PacienteRegistrado enB = registrar.registrar(datos("Ana", "Prueba", "90000001", null), OrigenPaciente.MANUAL,
                null, odontologo);

        assertThat(enB.reutilizado()).isFalse();
        assertThat(enB.paciente().id()).isNotEqualTo(enA.paciente().id());
    }

    @Test
    void unDniInvalidoNoRegistraNada() {
        assertCodigo(() -> registrar.registrar(datos("Ana", "Prueba", "90A00001", null), OrigenPaciente.MANUAL, null,
                odontologo), "DNI_INVALIDO");
        assertThat(acciones()).isEmpty();
    }

    private static DatosDePaciente datos(String nombre, String apellido, String dni, String email) {
        return new DatosDePaciente(nombre, apellido, dni, "011 15-0000-0001", email);
    }

    private Map<String, Object> columnas(UUID paciente) {
        return jdbc.queryForMap("""
                SELECT nombre, apellido, dni, telefono_e164, email::text AS email, origen, requiere_revision,
                       nombre_informado, apellido_informado, email_informado::text AS email_informado,
                       informado_turno_id
                  FROM paciente WHERE id = ?""", paciente);
    }
}
