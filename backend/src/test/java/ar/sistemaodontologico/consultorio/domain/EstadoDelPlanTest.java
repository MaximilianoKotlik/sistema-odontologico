package ar.sistemaodontologico.consultorio.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Estado efectivo del plan (C-20, tarea 1.1; design D1; spec plan-del-consultorio §Prueba vencida). */
class EstadoDelPlanTest {

    private static final ZoneId BUENOS_AIRES = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final LocalDate PRUEBA_HASTA = LocalDate.parse("2026-12-20");

    @Nested
    class PruebaVencida {

        @Test
        void elUltimoDiaSigueSiendoDePrueba() {
            // 23:59 del 2026-12-20 en Buenos Aires = 02:59 UTC del 21.
            EstadoDelPlan estado = EstadoDelPlan.en("PRUEBA", PRUEBA_HASTA, BUENOS_AIRES,
                    Instant.parse("2026-12-21T02:59:00Z"));

            assertThat(estado.vencido()).isFalse();
            assertThat(estado.soloLectura()).isFalse();
            assertThat(estado.planEfectivo()).isEqualTo(PlanDelConsultorio.PRUEBA);
            assertThat(estado.diasDePruebaRestantes()).isEqualTo(1);
        }

        @Test
        void elDiaSiguienteEstaVencidoAunqueLaTareaNoHayaCorrido() {
            // 00:01 del 2026-12-21 en Buenos Aires = 03:01 UTC.
            EstadoDelPlan estado = EstadoDelPlan.en("PRUEBA", PRUEBA_HASTA, BUENOS_AIRES,
                    Instant.parse("2026-12-21T03:01:00Z"));

            assertThat(estado.vencido()).isTrue();
            assertThat(estado.soloLectura()).isTrue();
            assertThat(estado.planEfectivo()).isEqualTo(PlanDelConsultorio.VENCIDO);
            assertThat(estado.diasDePruebaRestantes()).isNull();
        }

        @Test
        void laFechaSeCuentaEnLaZonaDelConsultorio() {
            // 2026-12-21T01:00Z: en Madrid ya es el 21 (vencido); en Buenos Aires todavía es el 20.
            Instant instante = Instant.parse("2026-12-21T01:00:00Z");

            assertThat(EstadoDelPlan.en("PRUEBA", PRUEBA_HASTA, ZoneId.of("Europe/Madrid"), instante).vencido())
                    .isTrue();
            assertThat(EstadoDelPlan.en("PRUEBA", PRUEBA_HASTA, BUENOS_AIRES, instante).vencido()).isFalse();
        }

        @Test
        void planVencido() {
            EstadoDelPlan estado = EstadoDelPlan.en("VENCIDO", PRUEBA_HASTA, BUENOS_AIRES,
                    Instant.parse("2026-12-01T12:00:00Z"));

            assertThat(estado.vencido()).isTrue();
            assertThat(estado.planEfectivo()).isEqualTo(PlanDelConsultorio.VENCIDO);
            assertThat(estado.diasDePruebaRestantes()).isNull();
        }

        @Test
        void unPlanActivoNoVenceAunqueLaPruebaHayaPasado() {
            EstadoDelPlan estado = EstadoDelPlan.en("ACTIVO", LocalDate.parse("2026-10-01"), BUENOS_AIRES,
                    Instant.parse("2026-12-01T12:00:00Z"));

            assertThat(estado.vencido()).isFalse();
            assertThat(estado.soloLectura()).isFalse();
            assertThat(estado.planEfectivo()).isEqualTo(PlanDelConsultorio.ACTIVO);
            assertThat(estado.diasDePruebaRestantes()).isNull();
        }

        @Test
        void unaPruebaSinFechaNoVence() {
            EstadoDelPlan estado = EstadoDelPlan.en("PRUEBA", null, BUENOS_AIRES, Instant.parse("2026-12-01T12:00:00Z"));

            assertThat(estado.vencido()).isFalse();
            assertThat(estado.diasDePruebaRestantes()).isNull();
        }
    }

    @Nested
    class DiasRestantes {

        @Test
        void cincoDiasCuandoElUltimoEsDentroDeCuatro() {
            // Mediodía del 2026-12-16 en Buenos Aires.
            EstadoDelPlan estado = EstadoDelPlan.en("PRUEBA", PRUEBA_HASTA, BUENOS_AIRES,
                    Instant.parse("2026-12-16T15:00:00Z"));

            assertThat(estado.diasDePruebaRestantes()).isEqualTo(5);
        }

        @Test
        void treintaYUnoElDiaDelRegistro() {
            EstadoDelPlan estado = EstadoDelPlan.en("PRUEBA", LocalDate.parse("2026-12-21"), BUENOS_AIRES,
                    Instant.parse("2026-11-21T15:00:00Z"));

            assertThat(estado.diasDePruebaRestantes()).isEqualTo(31);
        }
    }

    @Test
    void unPlanDesconocidoEsUnError() {
        assertThatThrownBy(() -> EstadoDelPlan.en("GRATIS", PRUEBA_HASTA, BUENOS_AIRES, Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
