package ar.sistemaodontologico.exportacion.web;

import static org.assertj.core.api.Assertions.assertThat;

import ar.sistemaodontologico.compartido.IntegracionPostgresTest;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import ar.sistemaodontologico.exportacion.application.ExportarDatos;
import ar.sistemaodontologico.exportacion.application.PedidoDeExportacion;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import pruebas.agenda.AgendaDePrueba;

/**
 * Medición de C-19 (tarea 6.4): 10 000 turnos y 1 000 pacientes ficticios. Fuera de la suite por defecto; se corre con
 * {@code VOLUMEN=1 ./gradlew test --tests '*VolumenDeExportacionTest' -i}.
 */
@Tag("volumen")
@EnabledIfEnvironmentVariable(named = "VOLUMEN", matches = "1")
class VolumenDeExportacionTest extends IntegracionPostgresTest {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ExportarDatos exportar;
    @Autowired
    private Clock clock;

    @Test
    void exportaDiezMilTurnosYMilPacientes() throws IOException {
        AgendaDePrueba agenda = AgendaDePrueba.nueva(jdbc);
        UUID prestacion = agenda.prestacion("Consulta", 30, true);
        jdbc.update("""
                INSERT INTO paciente (id, consultorio_id, nombre, apellido, dni, telefono_e164, origen)
                SELECT gen_random_uuid(), ?, 'Paciente', 'Prueba ' || n, (70000000 + n)::text, '+5491100000001', 'MANUAL'
                  FROM generate_series(1, 1000) n""", agenda.consultorio());
        jdbc.update("""
                INSERT INTO turno (id, consultorio_id, paciente_id, tipo_prestacion_id, inicio, fin, duracion_min, estado,
                                   origen)
                SELECT gen_random_uuid(), ?, p.id, ?, TIMESTAMPTZ '2030-01-01 12:00:00+00' + n * INTERVAL '30 minutes',
                       TIMESTAMPTZ '2030-01-01 12:30:00+00' + n * INTERVAL '30 minutes', 30, 'RESERVADO', 'MANUAL'
                  FROM generate_series(1, 10000) n
                  JOIN (SELECT id, row_number() OVER () - 1 AS fila FROM paciente WHERE consultorio_id = ?) p
                    ON p.fila = n % 1000""", agenda.consultorio(), prestacion, agenda.consultorio());
        PedidoDeExportacion pedido = new PedidoDeExportacion(new ConsultorioId(agenda.consultorio()), agenda.usuario(),
                "203.0.113.10", clock.instant());
        assertThat(exportar.reservar(pedido).permitida()).isTrue();
        List<MemoryPoolMXBean> heap = ManagementFactory.getMemoryPoolMXBeans().stream()
                .filter(pool -> pool.getType() == MemoryType.HEAP).toList();
        System.gc();
        heap.forEach(MemoryPoolMXBean::resetPeakUsage);
        long[] bytes = {0};
        OutputStream contador = new OutputStream() {
            @Override
            public void write(int valor) {
                bytes[0]++;
            }

            @Override
            public void write(byte[] contenido, int desde, int cantidad) {
                bytes[0] += cantidad;
            }
        };

        long inicio = System.nanoTime();
        exportar.generar(pedido, contador);
        long milisegundos = (System.nanoTime() - inicio) / 1_000_000;

        long picoMb = heap.stream().mapToLong(pool -> pool.getPeakUsage().getUsed()).sum() / (1024 * 1024);
        System.out.printf("VOLUMEN exportacion: %d ms, ZIP %d KB, pico de heap %d MB%n", milisegundos, bytes[0] / 1024,
                picoMb);
        assertThat(bytes[0]).isPositive();
    }
}
