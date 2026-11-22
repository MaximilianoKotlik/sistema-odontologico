package ar.sistemaodontologico.auditoria.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import ar.sistemaodontologico.compartido.domain.error.ValidacionDeNegocio;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Cursor firmado del registro (C-18, tarea 2.2; design D5). */
class CursorDeAuditoriaTest {

    private static final byte[] CLAVE = "clave-de-prueba-del-cursor-000000".getBytes(StandardCharsets.UTF_8);

    private final ConsultorioId consultorio = new ConsultorioId(UUID.randomUUID());
    private final CursorDeAuditoria cursor =
            new CursorDeAuditoria(Instant.parse("2026-11-10T13:00:00.123456Z"), UUID.randomUUID(), consultorio);

    @Test
    void codificaYDecodificaConPrecisionDeMicrosegundos() {
        assertThat(CursorDeAuditoria.decodificar(cursor.codificar(CLAVE), CLAVE, consultorio)).isEqualTo(cursor);
    }

    @Test
    void esOpacoYSeguroParaUrl() {
        assertThat(cursor.codificar(CLAVE)).matches("[A-Za-z0-9_.-]+");
    }

    @Test
    void rechazaUnaFirmaAlterada() {
        String codificado = cursor.codificar(CLAVE);
        char ultimo = codificado.charAt(codificado.length() - 2);
        String alterado = codificado.substring(0, codificado.length() - 2) + (ultimo == 'A' ? 'B' : 'A')
                + codificado.charAt(codificado.length() - 1);

        assertInvalido(alterado, CLAVE, consultorio);
    }

    @Test
    void rechazaUnContenidoAlteradoConLaFirmaOriginal() {
        String firma = cursor.codificar(CLAVE).split("\\.")[1];
        String otro = new CursorDeAuditoria(cursor.ocurridoAt().plusSeconds(1), cursor.id(), consultorio)
                .codificar(CLAVE).split("\\.")[0];

        assertInvalido(otro + "." + firma, CLAVE, consultorio);
    }

    @Test
    void rechazaOtraClave() {
        assertInvalido(cursor.codificar(CLAVE), "otra-clave".getBytes(StandardCharsets.UTF_8), consultorio);
    }

    @Test
    void rechazaElCursorDeOtroConsultorio() {
        assertInvalido(cursor.codificar(CLAVE), CLAVE, new ConsultorioId(UUID.randomUUID()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "basura", "a.b", "a.b.c", "%%%.###"})
    void rechazaTextoInvalido(String texto) {
        assertInvalido(texto, CLAVE, consultorio);
    }

    @Test
    void rechazaContenidoFirmadoQueNoEsUnCursor() throws Exception {
        String contenido = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"x\":1}".getBytes(StandardCharsets.UTF_8));
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(CLAVE, "HmacSHA256"));
        String firma = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(contenido.getBytes(StandardCharsets.US_ASCII)));

        assertInvalido(contenido + "." + firma, CLAVE, consultorio);
    }

    private static void assertInvalido(String texto, byte[] clave, ConsultorioId de) {
        assertThatThrownBy(() -> CursorDeAuditoria.decodificar(texto, clave, de))
                .isInstanceOf(ValidacionDeNegocio.class)
                .satisfies(error -> {
                    ValidacionDeNegocio validacion = (ValidacionDeNegocio) error;
                    assertThat(validacion.getCodigo()).isEqualTo("VALIDACION");
                    assertThat(validacion.getPropiedades().get("errores").toString()).contains("cursor");
                });
    }
}
