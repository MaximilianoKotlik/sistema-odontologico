package ar.sistemaodontologico.agenda.domain;

import static ar.sistemaodontologico.agenda.domain.IntervaloYDuracionTest.assertCodigo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.sistemaodontologico.compartido.domain.error.ConflictoDeNegocio;
import java.time.Instant;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

/** {@code Dni} y {@code Paciente} (C-10, tareas 3.5 y 3.6; spec pacientes, DD-24). Datos ficticios (regla dura 17). */
class PacienteTest {

    private static final PacienteId ID = new PacienteId(UUID.randomUUID());
    private static final Consentimiento CONSENTIMIENTO =
            new Consentimiento(Instant.parse("2026-11-20T12:00:00Z"), "2026-11-borrador");
    private static final DatosInformados INFORMADOS = new DatosInformados("Anna", "Prueba", "anna@example.com",
            Instant.parse("2026-11-20T12:00:00Z"), new TurnoId(UUID.randomUUID()));

    // --- Dni (3.5) ---

    @Test
    void elDniSeGuardaSoloConDigitos() {
        assertThat(new Dni("90.000.001").valor()).isEqualTo("90000001");
        assertThat(new Dni("9.000.001").valor()).isEqualTo("9000001");
        assertThat(new Dni(" 90 000 001 ").valor()).isEqualTo("90000001");
    }

    @Test
    void dniInvalido() {
        for (String invalido : new String[] {"90A00001", "123456", "123456789", "", "   ", "-9000001"}) {
            assertCodigo(() -> new Dni(invalido), "DNI_INVALIDO");
        }
        assertCodigo(() -> new Dni(null), "DNI_INVALIDO");
    }

    @Test
    void elDniNoApareceEnElTexto() {
        assertThat(new Dni("90000001").toString()).doesNotContain("90000001");
    }

    // --- Paciente (3.6) ---

    @Test
    void pacienteValido() {
        Paciente ana = Paciente.nuevo(ID, "  Ana ", " Prueba ", "90.000.001", "011 15-0000-0001", null,
                OrigenPaciente.MANUAL, null);

        assertThat(ana.nombre()).isEqualTo("Ana");
        assertThat(ana.apellido()).isEqualTo("Prueba");
        assertThat(ana.dni()).isEqualTo(new Dni("90000001"));
        assertThat(ana.telefonoE164()).isEqualTo("+5491100000001");
        assertThat(ana.email()).isNull();
        assertThat(ana.requiereRevision()).isFalse();
    }

    @Test
    void telefonoInvalidoUObligatorio() {
        assertCodigo(() -> manual("Ana", "Prueba", "123", null), "TELEFONO_INVALIDO");
        assertCodigo(() -> manual("Ana", "Prueba", null, null), "TELEFONO_INVALIDO");
    }

    @Test
    void nombreYApellidoDe1A80() {
        assertCodigo(() -> manual("   ", "Prueba", "+5491100000001", null), "VALIDACION");
        assertCodigo(() -> manual("Ana", "", "+5491100000001", null), "VALIDACION");
        assertCodigo(() -> manual("a".repeat(81), "Prueba", "+5491100000001", null), "VALIDACION");
        assertThatCode(() -> manual("a".repeat(80), "b".repeat(80), "+5491100000001", null)).doesNotThrowAnyException();
    }

    @Test
    void emailOpcionalEnMinusculas() {
        assertThat(manual("Ana", "Prueba", "+5491100000001", " Ana.Prueba@Example.com ").email())
                .isEqualTo("ana.prueba@example.com");
        assertThat(manual("Ana", "Prueba", "+5491100000001", "  ").email()).isNull();
        assertCodigo(() -> manual("Ana", "Prueba", "+5491100000001", "ana@"), "VALIDACION");
        assertCodigo(() -> manual("Ana", "Prueba", "+5491100000001", "ana prueba@example.com"), "VALIDACION");
    }

    @Test
    void porLinkNecesitaConsentimiento() {
        assertCodigo(() -> Paciente.nuevo(ID, "Ana", "Prueba", "90000001", "+5491100000001", null, OrigenPaciente.LINK,
                null), "VALIDACION");
        assertThat(Paciente.nuevo(ID, "Ana", "Prueba", "90000001", "+5491100000001", null, OrigenPaciente.LINK,
                CONSENTIMIENTO).consentimiento()).isEqualTo(CONSENTIMIENTO);
        assertCodigo(() -> new Consentimiento(Instant.parse("2026-11-20T12:00:00Z"), " "), "VALIDACION");
    }

    @Test
    void marcarParaRevisionNoCambiaLosDatosYGuardaLoInformado() {
        Paciente ana = manual("Ana", "Prueba", "+5491100000001", "ana@example.com");

        Paciente marcado = ana.marcadoParaRevision(INFORMADOS);

        assertThat(marcado.requiereRevision()).isTrue();
        assertThat(marcado.informados()).isEqualTo(INFORMADOS);
        assertThat(marcado).usingRecursiveComparison().ignoringFields("requiereRevision", "informados").isEqualTo(ana);
    }

    @Test
    void unaReservaNuevaAntesDeRevisarReemplazaLoInformado() {
        DatosInformados ultima = new DatosInformados("Anita", "Prueba", null, Instant.parse("2026-11-21T12:00:00Z"),
                null);

        Paciente marcado = manual("Ana", "Prueba", "+5491100000001", null).marcadoParaRevision(INFORMADOS)
                .marcadoParaRevision(ultima);

        assertThat(marcado.informados()).isEqualTo(ultima);
    }

    @Test
    void sinRevisionPendienteNoHayDatosInformados() {
        Paciente ana = manual("Ana", "Prueba", "+5491100000001", null);

        assertThat(ana.informados()).isNull();
        assertCodigo(() -> new Paciente(ana.id(), "Ana", "Prueba", ana.dni(), ana.telefonoE164(), null,
                OrigenPaciente.MANUAL, false, null, INFORMADOS), "VALIDACION");
    }

    @Test
    void losDatosInformadosSeNormalizan() {
        DatosInformados informados = new DatosInformados(" Anna ", " Ejemplo ", " Anna@Example.com ",
                Instant.parse("2026-11-20T12:00:00Z"), null);

        assertThat(informados.nombre()).isEqualTo("Anna");
        assertThat(informados.apellido()).isEqualTo("Ejemplo");
        assertThat(informados.email()).isEqualTo("anna@example.com");
        assertThat(informados.turnoOrigen()).isNull();
        assertCodigo(() -> new DatosInformados(" ", "Ejemplo", null, Instant.now(), null), "VALIDACION");
        assertCodigo(() -> new DatosInformados("Anna", "Ejemplo", "anna@", Instant.now(), null), "VALIDACION");
        assertThat(informados.toString()).doesNotContain("Anna").doesNotContain("Ejemplo").doesNotContain("example");
    }

    @Test
    void datosDistintosParaUnDniExistente() {
        Paciente ana = manual("Ana", "Prueba", "+5491100000001", null);

        assertThat(ana.datosDifierenDe("Ana María", "Prueba")).isTrue();
        assertThat(ana.datosDifierenDe("Ana", "Pruebas")).isTrue();
        assertThat(ana.datosDifierenDe(" ana ", "PRUEBA")).isFalse();
    }

    @Test
    void losEspaciosExtraNoHacenQueLosDatosDifieran() {
        // C-15, design D7: "José  María" y "jose maria" son la misma persona.
        Paciente jose = manual("José  María", "Pérez   Gómez", "+5491100000001", null);

        assertThat(jose.datosDifierenDe("jose maria", "perez gomez")).isFalse();
        assertThat(jose.datosDifierenDe("Otra Persona", "perez gomez")).isTrue();
    }

    @Test
    void losAcentosNoHacenQueLosDatosDifieran() {
        Paciente jose = manual("José", "Pérez", "+5491100000001", null);

        assertThat(jose.datosDifierenDe("jose", "perez")).isFalse();
        assertThat(jose.datosDifierenDe(" JOSÉ ", "PÉREZ ")).isFalse();
        assertThat(manual("Jose", "Perez", "+5491100000001", null).datosDifierenDe("José", "Pérez")).isFalse();
        assertThat(manual("Iñaki", "Muñoz", "+5491100000001", null).datosDifierenDe("Inaki", "Munoz")).isFalse();
        assertThat(jose.datosDifierenDe("Josefa", "Pérez")).isTrue();
    }

    // --- Revisión (C-17, tareas 3.1 y 3.2; spec revision-pacientes) ---

    @Test
    void confirmarLaRevisionMantieneLosDatosYBorraLoInformado() {
        Paciente ana = manual("Ana", "Prueba", "+5491100000001", "ana@example.com");

        Paciente revisado = ana.marcadoParaRevision(INFORMADOS).revisado();

        assertThat(revisado.requiereRevision()).isFalse();
        assertThat(revisado.informados()).isNull();
        assertThat(revisado).usingRecursiveComparison().isEqualTo(ana);
    }

    @Test
    void corregirCambiaNombreApellidoYEmailYBorraLoInformado() {
        Paciente marcado = manual("Ana", "Prueba", "+5491100000001", "ana@example.com")
                .marcadoParaRevision(INFORMADOS);

        Paciente corregido = marcado.corregido(" Anna ", "Ejemplo", " Anna@Example.com ");

        assertThat(corregido.nombre()).isEqualTo("Anna");
        assertThat(corregido.apellido()).isEqualTo("Ejemplo");
        assertThat(corregido.email()).isEqualTo("anna@example.com");
        assertThat(corregido.requiereRevision()).isFalse();
        assertThat(corregido.informados()).isNull();
        assertThat(corregido.dni()).isEqualTo(marcado.dni());
        assertThat(corregido.telefonoE164()).isEqualTo(marcado.telefonoE164());
        assertThat(corregido.id()).isEqualTo(marcado.id());
        assertThat(marcado.corregido("Ana", "Prueba", " ").email()).isNull();
    }

    @Test
    void corregirValidaLosDatos() {
        Paciente marcado = manual("Ana", "Prueba", "+5491100000001", null).marcadoParaRevision(INFORMADOS);

        assertCodigo(() -> marcado.corregido(" ", "Prueba", null), "VALIDACION");
        assertCodigo(() -> marcado.corregido("Ana", "b".repeat(81), null), "VALIDACION");
        assertCodigo(() -> marcado.corregido("Ana", "Prueba", "ana@"), "VALIDACION");
    }

    @Test
    void sinRevisionPendienteNoSeConfirmaNiSeCorrige() {
        Paciente ana = manual("Ana", "Prueba", "+5491100000001", null);

        assertSinRevisionPendiente(ana::revisado);
        assertSinRevisionPendiente(() -> ana.corregido("Anna", "Prueba", null));
    }

    @Test
    void laRepresentacionTextualNoTienePii() {
        Paciente ana = manual("Ana", "Prueba", "+5491100000001", "ana.prueba@example.com");

        assertThat(ana.toString())
                .doesNotContain("Ana").doesNotContain("Prueba").doesNotContain("90000001")
                .doesNotContain("+5491100000001").doesNotContain("example.com")
                .contains(ID.valor().toString());
    }

    private static void assertSinRevisionPendiente(ThrowingCallable accion) {
        assertThatThrownBy(accion).isInstanceOfSatisfying(ConflictoDeNegocio.class,
                error -> assertThat(error.getCodigo()).isEqualTo(Paciente.CODIGO_SIN_REVISION_PENDIENTE));
        assertThat(Paciente.CODIGO_SIN_REVISION_PENDIENTE).isEqualTo("SIN_REVISION_PENDIENTE");
    }

    private static Paciente manual(String nombre, String apellido, String telefono, String email) {
        return Paciente.nuevo(ID, nombre, apellido, "90000001", telefono, email, OrigenPaciente.MANUAL, null);
    }
}
