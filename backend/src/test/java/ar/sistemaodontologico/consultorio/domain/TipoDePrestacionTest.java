package ar.sistemaodontologico.consultorio.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.sistemaodontologico.compartido.domain.error.ValidacionDeNegocio;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Tipo de prestación (C-08, tarea 4.1; spec prestaciones, RN-AG-08 y RN-AG-10). */
class TipoDePrestacionTest {

    private static final UUID ID = UUID.randomUUID();

    @Test
    void unaPrestacionNuevaQuedaActiva() {
        TipoDePrestacion limpieza = TipoDePrestacion.nueva(ID, "Limpieza", 45, true, 1);

        assertThat(limpieza.activa()).isTrue();
        assertThat(limpieza.visiblePublica()).isTrue();
    }

    @Test
    void duracionDe5A480EnMultiplosDe5() {
        assertThatCode(() -> TipoDePrestacion.nueva(ID, "Corta", 5, true, 1)).doesNotThrowAnyException();
        assertThatCode(() -> TipoDePrestacion.nueva(ID, "Larga", 480, true, 1)).doesNotThrowAnyException();
        for (int invalida : new int[] {0, 47, 485, -5}) {
            assertThatThrownBy(() -> TipoDePrestacion.nueva(ID, "Prestación", invalida, true, 1))
                    .isInstanceOfSatisfying(ValidacionDeNegocio.class,
                            error -> assertThat(error.getCodigo()).isEqualTo("DURACION_INVALIDA"));
        }
    }

    @Test
    void elNombreSeGuardaSinEspaciosAlrededor() {
        assertThat(TipoDePrestacion.nueva(ID, "  Limpieza ", 45, true, 1).nombre()).isEqualTo("Limpieza");
    }

    @Test
    void desactivarYActivar() {
        TipoDePrestacion limpieza = TipoDePrestacion.nueva(ID, "Limpieza", 45, true, 1);

        TipoDePrestacion inactiva = limpieza.desactivada();
        assertThat(inactiva.activa()).isFalse();
        assertThat(inactiva.activada().activa()).isTrue();
    }

    @Test
    void editarConservaElEstadoYValida() {
        TipoDePrestacion inactiva = TipoDePrestacion.nueva(ID, "Limpieza", 45, true, 1).desactivada();

        TipoDePrestacion editada = inactiva.editada("Limpieza profunda", 60, false, 3);

        assertThat(editada).isEqualTo(new TipoDePrestacion(ID, "Limpieza profunda", 60, false, false, 3));
        assertThatThrownBy(() -> inactiva.editada("Limpieza", 47, true, 1)).isInstanceOf(ValidacionDeNegocio.class);
    }

    @Test
    void elMapaAuditableUsaSnakeCase() {
        assertThat(TipoDePrestacion.nueva(ID, "Limpieza", 45, true, 2).comoMapaAuditable())
                .containsEntry("nombre", "Limpieza")
                .containsEntry("duracion_min", 45)
                .containsEntry("visible_publica", true)
                .containsEntry("orden", 2)
                .containsEntry("activa", true);
    }
}
