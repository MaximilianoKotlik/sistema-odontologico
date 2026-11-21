package ar.sistemaodontologico.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Design D1 y D6 de C-05: bloqueo tras 5 fallos (RN-AU-03) y nueva contraseña. Datos ficticios. */
class UsuarioTest {

    private static final Instant AHORA = Instant.parse("2026-11-20T13:00:00Z");

    private final Usuario usuario = new Usuario(UUID.fromString("00000000-0000-7000-8000-000000000002"),
            new ConsultorioId(UUID.fromString("00000000-0000-7000-8000-000000000001")), "odontologa@example.com",
            "$2a$12$hash", 1, 0, null, AHORA.minus(Duration.ofDays(1)));

    @Test
    void cuatroFallosNoBloquean() {
        Usuario conFallos = fallar(usuario, 4, AHORA);

        assertThat(conFallos.intentosFallidos()).isEqualTo(4);
        assertThat(conFallos.estaBloqueado(AHORA)).isFalse();
    }

    @Test
    void elQuintoFalloBloqueaQuinceMinutosYReiniciaElContador() {
        Usuario bloqueado = fallar(usuario, 5, AHORA);

        assertThat(bloqueado.bloqueadoHasta()).isEqualTo(AHORA.plus(Duration.ofMinutes(15)));
        assertThat(bloqueado.intentosFallidos()).isZero();
        assertThat(bloqueado.estaBloqueado(AHORA)).isTrue();
        assertThat(bloqueado.estaBloqueado(AHORA.plus(Duration.ofMinutes(15)).minusNanos(1))).isTrue();
    }

    @Test
    void elBloqueoTerminaALosQuinceMinutos() {
        Usuario bloqueado = fallar(usuario, 5, AHORA);

        assertThat(bloqueado.estaBloqueado(AHORA.plus(Duration.ofMinutes(15)))).isFalse();
    }

    @Test
    void losIntentosDuranteElBloqueoNoLoExtiendenNiSumanFallos() {
        Usuario bloqueado = fallar(usuario, 5, AHORA);

        Usuario reintento = bloqueado.registrarFallo(AHORA.plus(Duration.ofMinutes(10)));

        assertThat(reintento).isEqualTo(bloqueado);
    }

    @Test
    void informaLosMinutosRestantesRedondeadosHaciaArriba() {
        Usuario bloqueado = fallar(usuario, 5, AHORA);

        assertThat(bloqueado.minutosDeBloqueoRestantes(AHORA)).isEqualTo(15);
        assertThat(bloqueado.minutosDeBloqueoRestantes(AHORA.plus(Duration.ofMinutes(10)).plusSeconds(1))).isEqualTo(5);
        assertThat(bloqueado.minutosDeBloqueoRestantes(AHORA.plus(Duration.ofMinutes(14)).plusSeconds(59))).isEqualTo(1);
    }

    @Test
    void elExitoReiniciaElContador() {
        Usuario conFallos = fallar(usuario, 3, AHORA);

        Usuario exitoso = conFallos.registrarExito();

        assertThat(exitoso.intentosFallidos()).isZero();
        assertThat(fallar(exitoso, 4, AHORA).estaBloqueado(AHORA)).isFalse();
    }

    @Test
    void despuesDeUnBloqueoVencidoLosFallosVuelvenAContarDesdeCero() {
        Usuario bloqueado = fallar(usuario, 5, AHORA);
        Instant despues = AHORA.plus(Duration.ofMinutes(16));

        Usuario unFallo = bloqueado.registrarFallo(despues);

        assertThat(unFallo.intentosFallidos()).isEqualTo(1);
        assertThat(unFallo.estaBloqueado(despues)).isFalse();
        assertThat(unFallo.bloqueadoHasta()).isNull();
    }

    @Test
    void establecerContrasenaSubeLaVersionYLimpiaElBloqueo() {
        Usuario bloqueado = fallar(usuario, 5, AHORA);
        Instant cambio = AHORA.plusSeconds(30);

        Usuario conNueva = bloqueado.establecerContrasena("$2a$12$nuevo", cambio);

        assertThat(conNueva.passwordHash()).isEqualTo("$2a$12$nuevo");
        assertThat(conNueva.credencialesVersion()).isEqualTo(2);
        assertThat(conNueva.passwordCambiadoAt()).isEqualTo(cambio);
        assertThat(conNueva.intentosFallidos()).isZero();
        assertThat(conNueva.bloqueadoHasta()).isNull();
        assertThat(conNueva.estaBloqueado(cambio)).isFalse();
    }

    @Test
    void toStringNoIncluyeElHashNiElEmail() {
        assertThat(usuario.toString()).doesNotContain("$2a$12$hash").doesNotContain("odontologa@example.com");
    }

    private static Usuario fallar(Usuario desde, int veces, Instant cuando) {
        Usuario actual = desde;
        for (int i = 0; i < veces; i++) {
            actual = actual.registrarFallo(cuando);
        }
        return actual;
    }
}
