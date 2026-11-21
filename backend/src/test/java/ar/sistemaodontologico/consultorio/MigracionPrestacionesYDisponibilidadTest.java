package ar.sistemaodontologico.consultorio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.sistemaodontologico.compartido.IntegracionPostgresTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Migración de tipo_prestacion y disponibilidad (C-08, tareas 2.1 y 2.2; design D5, D8 y D12). Datos ficticios
 * (regla dura 17).
 */
class MigracionPrestacionesYDisponibilidadTest extends IntegracionPostgresTest {

    @Autowired
    private JdbcTemplate jdbc;

    // --- tipo_prestacion ---

    @Test
    void prestacionConValoresPorDefecto() {
        UUID consultorio = insertarConsultorio();
        UUID prestacion = insertarPrestacion(consultorio, "Limpieza", 45);

        Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM tipo_prestacion WHERE id = ?", prestacion);

        assertThat(fila).containsEntry("activa", true).containsEntry("visible_publica", true);
    }

    @Test
    void laDuracionVaDe5A480EnMultiplosDe5() {
        UUID consultorio = insertarConsultorio();

        for (int invalida : new int[] {0, 47, 485}) {
            assertThatThrownBy(() -> insertarPrestacion(consultorio, "Prestación " + invalida, invalida))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("ck_tipo_prestacion_duracion");
        }
        assertThatCode(() -> insertarPrestacion(consultorio, "Corta", 5)).doesNotThrowAnyException();
        assertThatCode(() -> insertarPrestacion(consultorio, "Larga", 480)).doesNotThrowAnyException();
    }

    @Test
    void elNombreEsUnicoSinDistinguirMayusculasDentroDelConsultorio() {
        UUID consultorio = insertarConsultorio();
        insertarPrestacion(consultorio, "Limpieza", 45);

        assertThatThrownBy(() -> insertarPrestacion(consultorio, "LIMPIEZA", 30))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_tipo_prestacion_nombre");
        assertThatCode(() -> insertarPrestacion(insertarConsultorio(), "Limpieza", 45)).doesNotThrowAnyException();
    }

    @Test
    void elRolDeAplicacionNoBorraPrestaciones() {
        UUID prestacion = insertarPrestacion(insertarConsultorio(), "Control", 20);

        assertThatThrownBy(() -> jdbc.update("DELETE FROM tipo_prestacion WHERE id = ?", prestacion))
                .rootCause().hasMessageContaining("permission denied for table tipo_prestacion");
    }

    // --- disponibilidad ---

    @Test
    void elDiaVaDe1A7() {
        UUID consultorio = insertarConsultorio();

        for (int dia : new int[] {0, 8}) {
            assertThatThrownBy(() -> insertarFranja(consultorio, dia, "09:00", "13:00"))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("ck_disponibilidad_dia");
        }
    }

    @Test
    void elFinEsPosteriorAlInicio() {
        UUID consultorio = insertarConsultorio();

        assertThatThrownBy(() -> insertarFranja(consultorio, 1, "13:00", "09:00"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_disponibilidad_horas");
        assertThatThrownBy(() -> insertarFranja(consultorio, 1, "09:00", "09:00"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_disponibilidad_horas");
    }

    @Test
    void lasHorasSonMultiplosDe5Minutos() {
        UUID consultorio = insertarConsultorio();

        assertThatThrownBy(() -> insertarFranja(consultorio, 1, "09:03", "12:00"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_disponibilidad_multiplo_5");
        assertThatThrownBy(() -> insertarFranja(consultorio, 1, "09:00", "12:00:30"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_disponibilidad_multiplo_5");
    }

    @Test
    void laBaseRechazaFranjasSolapadasDelMismoDia() {
        UUID consultorio = insertarConsultorio();
        insertarFranja(consultorio, 1, "09:00", "13:00");

        assertThatThrownBy(() -> insertarFranja(consultorio, 1, "12:00", "15:00"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ex_disponibilidad_sin_solape");
    }

    @Test
    void contiguasDiasDistintosYOtroConsultorioPasan() {
        UUID consultorio = insertarConsultorio();
        insertarFranja(consultorio, 1, "09:00", "13:00");

        assertThatCode(() -> insertarFranja(consultorio, 1, "13:00", "15:00")).doesNotThrowAnyException();
        assertThatCode(() -> insertarFranja(consultorio, 2, "09:00", "13:00")).doesNotThrowAnyException();
        assertThatCode(() -> insertarFranja(insertarConsultorio(), 1, "09:00", "13:00")).doesNotThrowAnyException();
    }

    @Test
    void losIndicesEmpiezanPorElConsultorio() {
        // CONVENCIONES.md regla 4.
        assertThat(jdbc.queryForList("""
                SELECT indexdef FROM pg_indexes
                WHERE tablename IN ('tipo_prestacion', 'disponibilidad') AND indexname LIKE 'ix_%'""", String.class))
                .isNotEmpty()
                .allSatisfy(definicion -> assertThat(definicion).contains("(consultorio_id"));
    }

    private UUID insertarConsultorio() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO consultorio (id, nombre, slug, plan) VALUES (?, 'Consultorio Demo', ?, 'PRUEBA')",
                id, "config-" + id.toString().substring(0, 8));
        return id;
    }

    private UUID insertarPrestacion(UUID consultorio, String nombre, int duracion) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO tipo_prestacion (id, consultorio_id, nombre, duracion_min, orden) VALUES (?, ?, ?, ?, 1)",
                id, consultorio, nombre, duracion);
        return id;
    }

    private void insertarFranja(UUID consultorio, int dia, String inicio, String fin) {
        jdbc.update("""
                INSERT INTO disponibilidad (id, consultorio_id, dia_semana, hora_inicio, hora_fin)
                VALUES (?, ?, ?, CAST(? AS time), CAST(? AS time))""", UUID.randomUUID(), consultorio, dia, inicio, fin);
    }
}
