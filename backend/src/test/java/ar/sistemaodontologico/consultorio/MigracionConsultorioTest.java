package ar.sistemaodontologico.consultorio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.sistemaodontologico.compartido.IntegracionPostgresTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Migración V1 (C-02, tareas 2.1 a 2.3). Spec aislamiento-tenant — Requirement: El consultorio es la raíz
 * del tenant; spec slug-consultorio — Requirements: Formato del slug y Unicidad global del slug.
 */
class MigracionConsultorioTest extends IntegracionPostgresTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void creaLasExtensionesNecesarias() {
        List<String> extensiones = jdbc.queryForList(
                "SELECT extname FROM pg_extension WHERE extname IN ('btree_gist', 'citext')", String.class);

        assertThat(extensiones).containsExactlyInAnyOrder("btree_gist", "citext");
    }

    @Test
    void altaConValoresPorDefecto() {
        UUID id = insertarConsultorio("defaults-" + sufijo());

        Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM consultorio WHERE id = ?", id);

        assertThat(fila)
                .containsEntry("zona_horaria", "America/Argentina/Buenos_Aires")
                .containsEntry("anticipacion_cancelacion_min", 1440)
                .containsEntry("anticipacion_reserva_min", 120)
                .containsEntry("horizonte_reserva_dias", 60)
                .containsEntry("paso_huecos_min", 15)
                .containsEntry("reserva_publica_activa", true)
                .containsEntry("version", 0L);
        assertThat(fila.get("created_at")).isNotNull();
        assertThat(fila.get("updated_at")).isNotNull();
    }

    @Test
    void rechazaAnticipacionDeCancelacionFueraDeRango() {
        UUID id = insertarConsultorio("cancelacion-" + sufijo());

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE consultorio SET anticipacion_cancelacion_min = 10081 WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_consultorio_anticipacion_cancelacion");
    }

    @Test
    void rechazaPasoDeHuecosNoPermitido() {
        UUID id = insertarConsultorio("paso-" + sufijo());

        assertThatThrownBy(() -> jdbc.update("UPDATE consultorio SET paso_huecos_min = 25 WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_consultorio_paso_huecos");
    }

    @Test
    void rechazaPlanDesconocido() {
        UUID id = insertarConsultorio("plan-" + sufijo());

        assertThatThrownBy(() -> jdbc.update("UPDATE consultorio SET plan = 'GRATIS' WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_consultorio_plan");
    }

    @Test
    void rechazaSlugConFormatoInvalido() {
        for (String slugInvalido : List.of("ab", "-perez", "perez-", "perez--sur", "Perez", "perez_sur")) {
            assertThatThrownBy(() -> insertarConsultorio(slugInvalido))
                    .as("slug %s", slugInvalido)
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("ck_consultorio_slug");
        }
    }

    @Test
    void rechazaSlugDeMasDeCuarentaCaracteres() {
        // Lo frena el largo de la columna antes que el CHECK; ambos protegen el límite de RN-TE-03.
        assertThatThrownBy(() -> insertarConsultorio("a".repeat(41)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("value too long");
    }

    @Test
    void rechazaSlugDuplicado() {
        String slug = "duplicado-" + sufijo();
        insertarConsultorio(slug);

        assertThatThrownBy(() -> insertarConsultorio(slug))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_consultorio_slug");
    }

    @Test
    void elRolDeAplicacionLeeEscribeYActualizaPeroNoBorraConsultorios() {
        UUID id = insertarConsultorio("permisos-" + sufijo());

        jdbc.update("UPDATE consultorio SET nombre = 'Otro nombre' WHERE id = ?", id);
        assertThat(jdbc.queryForObject("SELECT nombre FROM consultorio WHERE id = ?", String.class, id))
                .isEqualTo("Otro nombre");

        assertThatThrownBy(() -> jdbc.update("DELETE FROM consultorio WHERE id = ?", id))
                .rootCause()
                .hasMessageContaining("permission denied for table consultorio");
    }

    private UUID insertarConsultorio(String slug) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO consultorio (id, nombre, slug, plan) VALUES (?, 'Consultorio Demo', ?, 'PRUEBA')",
                id, slug);
        return id;
    }

    private static String sufijo() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
