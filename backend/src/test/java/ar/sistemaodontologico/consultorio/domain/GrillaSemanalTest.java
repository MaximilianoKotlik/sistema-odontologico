package ar.sistemaodontologico.consultorio.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.sistemaodontologico.compartido.domain.error.ValidacionDeNegocio;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Franjas y grilla semanal (C-08, tarea 5.1; spec disponibilidad-semanal, design D5). */
class GrillaSemanalTest {

    @Test
    void franjaValida() {
        assertThatCode(() -> franja(1, "09:00", "13:00")).doesNotThrowAnyException();
        assertThatCode(() -> franja(7, "00:00", "23:55")).doesNotThrowAnyException();
    }

    @Test
    void franjasInvalidas() {
        assertFranjaInvalida(() -> franja(0, "09:00", "13:00"));
        assertFranjaInvalida(() -> franja(8, "09:00", "13:00"));
        assertFranjaInvalida(() -> franja(1, "13:00", "09:00"));
        assertFranjaInvalida(() -> franja(1, "09:00", "09:00"));
        assertFranjaInvalida(() -> franja(1, "09:03", "12:00"));
        assertFranjaInvalida(() -> franja(1, "09:00", "12:00:30"));
    }

    @Test
    void ordenaPorDiaEInicio() {
        GrillaSemanal grilla = new GrillaSemanal(List.of(franja(2, "09:00", "13:00"), franja(1, "15:00", "19:00"),
                franja(1, "09:00", "13:00")));

        assertThat(grilla.franjas()).containsExactly(franja(1, "09:00", "13:00"), franja(1, "15:00", "19:00"),
                franja(2, "09:00", "13:00"));
    }

    @Test
    void solapadasElMismoDiaConLosDiasEnConflicto() {
        assertThatThrownBy(() -> new GrillaSemanal(List.of(franja(3, "10:00", "11:00"), franja(1, "09:00", "13:00"),
                franja(1, "12:00", "15:00"), franja(3, "10:30", "12:00"), franja(2, "09:00", "13:00"))))
                .isInstanceOfSatisfying(ValidacionDeNegocio.class, error -> {
                    assertThat(error.getCodigo()).isEqualTo("FRANJAS_SOLAPADAS");
                    assertThat(error.getPropiedades()).isEqualTo(Map.of("dias", List.of(1, 3)));
                });
    }

    @Test
    void unaFranjaDentroDeOtraTambienSeSolapa() {
        assertThatThrownBy(() -> new GrillaSemanal(List.of(franja(1, "09:00", "19:00"), franja(1, "10:00", "11:00"))))
                .isInstanceOf(ValidacionDeNegocio.class);
    }

    @Test
    void contiguasYMismoHorarioEnDiasDistintos() {
        assertThatCode(() -> new GrillaSemanal(List.of(franja(1, "09:00", "13:00"), franja(1, "13:00", "15:00"),
                franja(2, "09:00", "13:00")))).doesNotThrowAnyException();
    }

    @Test
    void laMismaGrillaEnOtroOrdenEsIgual() {
        assertThat(new GrillaSemanal(List.of(franja(2, "09:00", "13:00"), franja(1, "09:00", "13:00"))))
                .isEqualTo(new GrillaSemanal(List.of(franja(1, "09:00", "13:00"), franja(2, "09:00", "13:00"))));
    }

    @Test
    void mapaAuditableConLasFranjasCompletas() {
        assertThat(new GrillaSemanal(List.of(franja(1, "09:00", "13:00"))).comoMapaAuditable())
                .isEqualTo(Map.of("franjas", List.of(Map.of("dia_semana", 1, "hora_inicio", "09:00", "hora_fin", "13:00"))));
    }

    private static Franja franja(int dia, String inicio, String fin) {
        return new Franja(dia, LocalTime.parse(inicio), LocalTime.parse(fin));
    }

    private static void assertFranjaInvalida(org.assertj.core.api.ThrowableAssert.ThrowingCallable accion) {
        assertThatThrownBy(accion).isInstanceOfSatisfying(ValidacionDeNegocio.class,
                error -> assertThat(error.getCodigo()).isEqualTo("FRANJA_INVALIDA"));
    }
}
