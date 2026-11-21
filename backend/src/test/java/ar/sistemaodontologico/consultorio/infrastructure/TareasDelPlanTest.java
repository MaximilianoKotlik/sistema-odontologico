package ar.sistemaodontologico.consultorio.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import ar.sistemaodontologico.consultorio.application.ActivarPlan;
import ar.sistemaodontologico.consultorio.application.VencerPruebas;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** La tarea diaria y la operación interna de activación (C-20, tareas 2.2 y 2.3; design D2 y D3). */
class TareasDelPlanTest {

    @Test
    void laTareaCorreALasTresDeBuenosAiresYSePuedeConfigurar() throws Exception {
        Scheduled programacion = TareaDeVencimiento.class.getDeclaredMethod("correr").getAnnotation(Scheduled.class);

        assertThat(programacion.cron()).isEqualTo("${app.plan.vencimiento.cron:0 0 3 * * *}");
        assertThat(programacion.zone()).isEqualTo("America/Argentina/Buenos_Aires");
        assertThat(ConfiguracionDeTareasDelPlan.class).hasAnnotation(EnableScheduling.class);
    }

    @Test
    void laTareaVenceConLaHoraDelReloj() {
        VencerPruebas vencimiento = mock(VencerPruebas.class);
        Instant ahora = Instant.parse("2026-12-21T06:00:00Z");

        new TareaDeVencimiento(vencimiento, Clock.fixed(ahora, ZoneOffset.UTC)).correr();

        verify(vencimiento).vencer(ahora);
    }

    @Test
    void laOperacionSoloExisteConElPerfilOperacion() {
        assertThat(OperacionActivarPlan.class.getAnnotation(Profile.class).value()).containsExactly("operacion");
    }

    @Test
    void laOperacionActivaElConsultorioPedido() throws Exception {
        ActivarPlan activacion = mock(ActivarPlan.class);
        UUID consultorio = UUID.fromString("00000000-0000-7000-8000-000000000001");

        new OperacionActivarPlan(activacion)
                .run(new DefaultApplicationArguments("--operacion.consultorio=" + consultorio));

        verify(activacion).activar(new ConsultorioId(consultorio));
    }

    @Test
    void sinConsultorioLaOperacionFallaSinTocarNada() {
        ActivarPlan activacion = mock(ActivarPlan.class);

        assertThatThrownBy(() -> new OperacionActivarPlan(activacion).run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--operacion.consultorio");
        assertThatThrownBy(() -> new OperacionActivarPlan(activacion)
                .run(new DefaultApplicationArguments("--operacion.consultorio=no-es-un-uuid")))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(activacion);
    }
}
