package ar.sistemaodontologico.auditoria.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import ar.sistemaodontologico.auditoria.application.LecturaDeAuditoria;
import ar.sistemaodontologico.auditoria.application.LecturaDeAuditoria.CriteriosDeRegistro;
import ar.sistemaodontologico.compartido.IntegracionPostgresTest;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import pruebas.agenda.AgendaDePrueba;

/**
 * Medición de C-18 (tarea 3.4): 50 000 eventos ficticios y las tres variantes de la consulta. Fuera de la suite por
 * defecto; se corre con {@code VOLUMEN=1 ./gradlew test --tests '*VolumenDeAuditoriaTest' -i}.
 */
@Tag("volumen")
@EnabledIfEnvironmentVariable(named = "VOLUMEN", matches = "1")
class VolumenDeAuditoriaTest extends IntegracionPostgresTest {

    private static final int LIMITE = 51;

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private LecturaDeAuditoria lectura;

    @Test
    void lasTresVariantesConCincuentaMilEventos() throws SQLException {
        AgendaDePrueba agenda = AgendaDePrueba.nueva(jdbc);
        UUID turno = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO evento_auditoria (id, consultorio_id, ocurrido_at, actor_tipo, entidad, entidad_id, accion)
                SELECT gen_random_uuid(), ?, TIMESTAMPTZ '2026-01-01 12:00:00+00' + n * INTERVAL '10 minutes', 'SISTEMA',
                       'turno', CASE WHEN n % 500 = 0 THEN ?::uuid ELSE gen_random_uuid() END,
                       CASE WHEN n % 10 = 0 THEN 'TURNO_REPROGRAMADO' ELSE 'TURNO_CREADO' END
                  FROM generate_series(1, 50000) n""", agenda.consultorio(), turno);
        try (Connection owner = DriverManager.getConnection(POSTGRES.getJdbcUrl(), OWNER_USER, OWNER_PASSWORD);
                Statement sentencia = owner.createStatement()) {
            sentencia.execute("ANALYZE evento_auditoria");
        }
        ConsultorioId consultorio = new ConsultorioId(agenda.consultorio());

        long sinFiltros = medir("sin filtros", () -> lectura.listarRegistro(consultorio,
                new CriteriosDeRegistro(null, null, null, List.of(), null, null, LIMITE)).size());
        long porTurno = medir("por turno", () -> lectura.listarRegistro(consultorio,
                new CriteriosDeRegistro(turno, null, null, List.of(), null, null, LIMITE)).size());
        long porAccion = medir("por acción", () -> lectura.listarRegistro(consultorio,
                new CriteriosDeRegistro(null, null, null, List.of("TURNO_REPROGRAMADO"), null, null, LIMITE)).size());

        explicar("sin filtros", agenda.consultorio(), "");
        explicar("por turno", agenda.consultorio(), " AND entidad = 'turno' AND entidad_id = '" + turno + "'");
        explicar("por acción", agenda.consultorio(), " AND accion = 'TURNO_REPROGRAMADO'");
        assertThat(List.of(sinFiltros, porTurno, porAccion)).allMatch(ms -> ms < 100);
    }

    private static long medir(String variante, Supplier<Integer> consulta) {
        consulta.get(); // La primera calienta el plan y la caché.
        long inicio = System.nanoTime();
        int filas = consulta.get();
        long milisegundos = (System.nanoTime() - inicio) / 1_000_000;
        System.out.printf("VOLUMEN auditoria %s: %d ms, %d filas%n", variante, milisegundos, filas);
        return milisegundos;
    }

    private void explicar(String variante, UUID consultorio, String filtro) {
        List<String> plan = jdbc.queryForList("EXPLAIN (ANALYZE, COSTS OFF) SELECT id FROM evento_auditoria "
                + "WHERE consultorio_id = '" + consultorio + "'" + filtro
                + " ORDER BY ocurrido_at DESC, id DESC LIMIT " + LIMITE, String.class);
        System.out.println("VOLUMEN plan " + variante);
        plan.forEach(linea -> System.out.println("VOLUMEN   " + linea));
    }
}
