package ar.sistemaodontologico.compartido.domain.auditoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Spec auditoria — Requirements: Contenido de un evento y Los eventos nunca contienen secretos. */
class CambioAuditadoTest {

    private static final ConsultorioId CONSULTORIO = new ConsultorioId(UUID.randomUUID());
    private static final Actor ODONTOLOGO = new Actor(ActorTipo.ODONTOLOGO, UUID.randomUUID());

    @Test
    void cambioCompleto() {
        UUID entidadId = UUID.randomUUID();

        CambioAuditado cambio = new CambioAuditado(CONSULTORIO, ODONTOLOGO, "consultorio", entidadId,
                "CONSULTORIO_CONFIG_CAMBIADA", Map.of("paso_huecos_min", 15), Map.of("paso_huecos_min", 30), "192.0.2.10");

        assertThat(cambio.consultorio()).isEqualTo(CONSULTORIO);
        assertThat(cambio.valorAnterior()).containsEntry("paso_huecos_min", 15);
        assertThat(cambio.valorNuevo()).containsEntry("paso_huecos_min", 30);
    }

    @Test
    void valoresYIpSonOpcionales() {
        CambioAuditado cambio = new CambioAuditado(CONSULTORIO, Actor.sistema(), "consultorio", UUID.randomUUID(),
                "PLAN_VENCIDO", null, null, null);

        assertThat(cambio.valorAnterior()).isNull();
        assertThat(cambio.actor().id()).isNull();
    }

    @Test
    void sinConsultorioFalla() {
        assertThatThrownBy(() -> new CambioAuditado(null, ODONTOLOGO, "consultorio", UUID.randomUUID(),
                "CONSULTORIO_CONFIG_CAMBIADA", null, null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void sinAccionFalla() {
        assertThatThrownBy(() -> new CambioAuditado(CONSULTORIO, ODONTOLOGO, "consultorio", UUID.randomUUID(),
                null, null, null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"turno_creado", "Turno-Creado", "", "TURNO CREADO"})
    void laAccionDebeSerMayusculasConGuionesBajos(String accion) {
        assertThatThrownBy(() -> new CambioAuditado(CONSULTORIO, ODONTOLOGO, "turno", UUID.randomUUID(),
                accion, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"password", "password_hash", "token", "refresh_token", "token_hash", "PASSWORD"})
    void rechazaClavesConSecretos(String clave) {
        assertThatThrownBy(() -> new CambioAuditado(CONSULTORIO, ODONTOLOGO, "usuario", UUID.randomUUID(),
                "PASSWORD_CAMBIADA", null, Map.of(clave, "x"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("x\"");
    }

    @Test
    void rechazaSecretosAnidados() {
        Map<String, Object> anidado = Map.of("credenciales", Map.of("password", "x"));

        assertThatThrownBy(() -> new CambioAuditado(CONSULTORIO, ODONTOLOGO, "usuario", UUID.randomUUID(),
                "PASSWORD_CAMBIADA", anidado, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void losValoresSonUnaCopiaInmutableYAceptanNulos() {
        Map<String, Object> original = new HashMap<>();
        original.put("nota", null);

        CambioAuditado cambio = new CambioAuditado(CONSULTORIO, ODONTOLOGO, "turno", UUID.randomUUID(),
                "TURNO_NOTA_CAMBIADA", original, null, null);
        original.put("otra", "x");

        assertThat(cambio.valorAnterior()).containsOnlyKeys("nota");
        assertThatThrownBy(() -> cambio.valorAnterior().put("y", 1)).isInstanceOf(UnsupportedOperationException.class);
    }
}
