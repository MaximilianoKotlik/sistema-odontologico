package ar.sistemaodontologico.reserva.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Token de gestión del paciente (C-15, tarea 3.1, design D4). */
class TokenDeGestionTest {

    private static final String VALIDO = "q7Zt_9xYv2LmN0pK-4sDfGhJ8wErTyUi1oPa3bCdE5f";

    @Test
    void unTokenEmitidoTiene32BytesEnBase64UrlDe43Caracteres() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String secreto = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        TokenDeGestion token = TokenDeGestion.emitido(secreto);

        assertThat(token.valor()).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(Base64.getUrlDecoder().decode(token.valor())).hasSize(32);
    }

    @Test
    void elHashEsElSha256EnHexadecimal() throws NoSuchAlgorithmException {
        String esperado = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(VALIDO.getBytes(StandardCharsets.UTF_8)));

        assertThat(TokenDeGestion.leer(VALIDO).orElseThrow().hash()).hasSize(64).isEqualTo(esperado);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "corto", "q7Zt_9xYv2LmN0pK-4sDfGhJ8wErTyUi1oPa3bCdE5",
        "q7Zt_9xYv2LmN0pK-4sDfGhJ8wErTyUi1oPa3bCdE5fX", "q7Zt_9xYv2LmN0pK+4sDfGhJ8wErTyUi1oPa3bCdE5f",
        "q7Zt 9xYv2LmN0pK-4sDfGhJ8wErTyUi1oPa3bCdE5f"})
    void unFormatoInvalidoNoSeLee(String crudo) {
        assertThat(TokenDeGestion.leer(crudo)).isEmpty();
    }

    @Test
    void nuloNoSeLee() {
        assertThat(TokenDeGestion.leer(null)).isEmpty();
    }

    @Test
    void unSecretoInvalidoNoSeEmite() {
        assertThatThrownBy(() -> TokenDeGestion.emitido("corto")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toStringNoExponeElValor() {
        assertThat(TokenDeGestion.leer(VALIDO).orElseThrow().toString()).doesNotContain(VALIDO);
    }
}
