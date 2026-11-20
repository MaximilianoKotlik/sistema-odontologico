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
    void conservaIdentificadoresUuid() {
        String texto = "consultorio 01936b2e-7c4f-7d4a-9e1b-3f6a2c1d0e9f";

        assertThat(EnmascaradorDePii.enmascarar(texto)).isEqualTo(texto);
    }

    @Test
    void toleraNulo() {
        assertThat(EnmascaradorDePii.enmascarar(null)).isNull();
    }
}
