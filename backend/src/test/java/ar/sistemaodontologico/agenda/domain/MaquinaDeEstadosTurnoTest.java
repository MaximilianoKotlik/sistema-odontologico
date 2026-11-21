package ar.sistemaodontologico.agenda.domain;

import static ar.sistemaodontologico.agenda.domain.Agenda.lunesA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.sistemaodontologico.compartido.domain.error.ConflictoDeNegocio;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Matriz completa estado × acción (C-10, tarea 3.2; spec turnos, Requirement: Estados y transiciones). */
class MaquinaDeEstadosTurnoTest {

    private static final Instant INICIO = lunesA("10:00");
    private static final Instant ANTES = INICIO.minusSeconds(3600);
    private static final Instant DESPUES = INICIO.plusSeconds(600);

    /** Resultado esperado de cada acción para un turno cuyo inicio ya pasó; {@code null} = transición inválida. */
    private static final Map<EstadoTurno, Map<AccionTurno, EstadoTurno>> VALIDAS = Map.of(
            EstadoTurno.RESERVADO, Map.of(
                    AccionTurno.CONFIRMAR, EstadoTurno.CONFIRMADO,
                    AccionTurno.CANCELAR, EstadoTurno.CANCELADO,
                    AccionTurno.MARCAR_AUSENTE, EstadoTurno.AUSENTE,
                    AccionTurno.REPROGRAMAR, EstadoTurno.RESERVADO,
                    AccionTurno.AJUSTAR_DURACION, EstadoTurno.RESERVADO),
            EstadoTurno.CONFIRMADO, Map.of(
                    AccionTurno.CANCELAR, EstadoTurno.CANCELADO,
                    AccionTurno.MARCAR_AUSENTE, EstadoTurno.AUSENTE,
                    AccionTurno.REPROGRAMAR, EstadoTurno.CONFIRMADO,
                    AccionTurno.AJUSTAR_DURACION, EstadoTurno.CONFIRMADO),
            EstadoTurno.CANCELADO, Map.of(),
            EstadoTurno.AUSENTE, Map.of());

    @ParameterizedTest
    @EnumSource(EstadoTurno.class)
    void matrizCompleta(EstadoTurno estado) {
        for (AccionTurno accion : AccionTurno.values()) {
            EstadoTurno esperado = VALIDAS.get(estado).get(accion);
            if (esperado == null) {
                assertThatThrownBy(() -> MaquinaDeEstadosTurno.siguiente(estado, accion, INICIO, DESPUES))
                        .as("%s + %s", estado, accion)
                        .isInstanceOfSatisfying(ConflictoDeNegocio.class,
                                error -> assertThat(error.getCodigo()).isEqualTo("TRANSICION_INVALIDA"));
            } else {
                assertThat(MaquinaDeEstadosTurno.siguiente(estado, accion, INICIO, DESPUES))
                        .as("%s + %s", estado, accion).isEqualTo(esperado);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(value = EstadoTurno.class, names = {"RESERVADO", "CONFIRMADO"})
    void ausenteSoloDespuesDelInicio(EstadoTurno activo) {
        assertThatThrownBy(() -> MaquinaDeEstadosTurno.siguiente(activo, AccionTurno.MARCAR_AUSENTE, INICIO, ANTES))
                .isInstanceOf(ConflictoDeNegocio.class);
        assertThatThrownBy(() -> MaquinaDeEstadosTurno.siguiente(activo, AccionTurno.MARCAR_AUSENTE, INICIO,
                INICIO.minusMillis(1))).isInstanceOf(ConflictoDeNegocio.class);
        assertThat(MaquinaDeEstadosTurno.siguiente(activo, AccionTurno.MARCAR_AUSENTE, INICIO, INICIO))
                .isEqualTo(EstadoTurno.AUSENTE);
    }
}
