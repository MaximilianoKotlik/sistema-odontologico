package ar.sistemaodontologico.compartido.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** RN-GL-01: los datos personales no llegan a los logs. Solo datos ficticios (regla dura 17). */
class EnmascaradorDePiiTest {

    @Test
    void enmascaraTelefonosEnCualquierFormato() {
        assertThat(EnmascaradorDePii.enmascarar("tel +5491100000099 o 011 15-0000-0001 fin"))
                .doesNotContain("5491100000099")
                .doesNotContain("0000-0001")
                .startsWith("tel ")
                .endsWith(" fin");
    }

    @Test
    void enmascaraEmails() {
        assertThat(EnmascaradorDePii.enmascarar("mail paciente.prueba@example.com enviado"))
                .isEqualTo("mail ***@*** enviado");
    }

    @Test
    void conservaTextoSinDatosPersonalesYNumerosCortos() {
        assertThat(EnmascaradorDePii.enmascarar("turno 42 de las 10:30 en el box 2"))
                .isEqualTo("turno 42 de las 10:30 en el box 2");
    }

    @Test
    void conservaFechasIso() {
        assertThat(EnmascaradorDePii.enmascarar("turno del 2026-11-20 y prueba hasta 2026-12-20, tel 011 0000-0001"))
                .contains("2026-11-20")
                .contains("2026-12-20")
                .doesNotContain("0000-0001");
    }

    @Test
    void conservaIdentificadoresUuid() {
        String texto = "consultorio 01936b2e-7c4f-7d4a-9e1b-3f6a2c1d0e9f";

        assertThat(EnmascaradorDePii.enmascarar(texto)).isEqualTo(texto);
    }

    @Test
    void enmascaraTokensDe256BitsEnBase64Url() {
        // 32 bytes en base64url sin relleno = 43 caracteres, como los refresh y los tokens de recuperación (C-03).
        String token = "q7Zt_9xYv2LmN0pK-4sDfGhJ8wErTyUi1oPa3bCdE5f";

        assertThat(EnmascaradorDePii.enmascarar("refresh " + token + " rotado"))
                .isEqualTo("refresh *** rotado");
    }

    @Test
    void enmascaraHashesSha256EnHexadecimal() {
        String hash = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08";

        assertThat(EnmascaradorDePii.enmascarar("token_hash=" + hash)).isEqualTo("token_hash=***");
    }

    @Test
    void conservaPalabrasYUuidMasCortosQueUnToken() {
        String texto = "excepcion_de_negocio_conocida id=01936b2e-7c4f-7d4a-9e1b-3f6a2c1d0e9f";

        assertThat(EnmascaradorDePii.enmascarar(texto)).isEqualTo(texto);
    }

    @Test
    void toleraNulo() {
        assertThat(EnmascaradorDePii.enmascarar(null)).isNull();
    }
}
