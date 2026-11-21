package ar.sistemaodontologico.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec recuperacion-contrasena — Requirements: Token de recuperación de un solo uso y Token inválido (design D3). */
class TokenDeRecuperacionTest {

    private static final Instant EMITIDO = Instant.parse("2026-11-20T13:00:00Z");
    private static final ConsultorioId CONSULTORIO = new ConsultorioId(UUID.fromString("00000000-0000-7000-8000-000000000001"));
    private static final UUID USUARIO = UUID.fromString("00000000-0000-7000-8000-000000000002");
    private static final UUID ID = UUID.fromString("00000000-0000-7000-8000-000000000003");

    private final TokenDeRecuperacion emitido = TokenDeRecuperacion.emitir(ID, CONSULTORIO, USUARIO, EMITIDO);

    @Test
    void venceTreintaMinutosDespuesDeEmitirse() {
        assertThat(emitido.expiraAt()).isEqualTo(EMITIDO.plus(Duration.ofMinutes(30)));
        assertThat(emitido.usadoAt()).isNull();
        assertThat(emitido.revocadoAt()).isNull();
    }

    @Test
    void recienEmitidoEstaVigente() {
        assertThat(emitido.vigente(EMITIDO)).isTrue();
        assertThat(emitido.vigente(EMITIDO.plus(Duration.ofMinutes(29)).plusSeconds(59))).isTrue();
    }

    @Test
    void aLosTreintaMinutosEstaVencidoYDejaDeEstarVigente() {
        Instant limite = EMITIDO.plus(Duration.ofMinutes(30));

        assertThat(emitido.vencido(limite.minusNanos(1))).isFalse();
        assertThat(emitido.vencido(limite)).isTrue();
        assertThat(emitido.vigente(limite)).isFalse();
    }

    @Test
    void usadoDejaDeEstarVigenteAunqueNoHayaVencido() {
        TokenDeRecuperacion usado = new TokenDeRecuperacion(ID, CONSULTORIO, USUARIO,
                emitido.expiraAt(), EMITIDO.plusSeconds(60), null);

        assertThat(usado.usado()).isTrue();
        assertThat(usado.revocado()).isFalse();
        assertThat(usado.vigente(EMITIDO.plusSeconds(120))).isFalse();
    }

    @Test
    void revocadoDejaDeEstarVigenteAunqueNoHayaVencido() {
        TokenDeRecuperacion revocado = new TokenDeRecuperacion(ID, CONSULTORIO, USUARIO,
                emitido.expiraAt(), null, EMITIDO.plusSeconds(60));

        assertThat(revocado.revocado()).isTrue();
        assertThat(revocado.usado()).isFalse();
        assertThat(revocado.vigente(EMITIDO.plusSeconds(120))).isFalse();
    }
}
