package pruebas.agenda;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.jdbc.core.JdbcTemplate;
import pruebas.seguridad.CuentasDePrueba;

/**
 * Solo de test (C-11): arma con SQL directo un consultorio con prestaciones, disponibilidad, pacientes, turnos y
 * bloqueos, para probar los casos de uso de agenda. Datos ficticios evidentes (regla dura 17): DNI {@code 9xxxxxxx},
 * teléfonos {@code +54911000000xx} y emails {@code @example.com}.
 */
public final class AgendaDePrueba {

    /** Lunes 2026-11-23 a las 00:00 en Buenos Aires (03:00 UTC). Los tests corren en esa semana. */
    public static final Instant LUNES = Instant.parse("2026-11-23T03:00:00Z");

    // Arranca en un valor al azar: el contenedor de Postgres lo comparte toda la suite.
    private static final AtomicInteger DNI = new AtomicInteger(ThreadLocalRandom.current().nextInt(9_000_000));

    private final JdbcTemplate jdbc;
    private final UUID consultorio;
    private final UUID usuario;

    private AgendaDePrueba(JdbcTemplate jdbc, UUID consultorio, UUID usuario) {
        this.jdbc = jdbc;
        this.consultorio = consultorio;
        this.usuario = usuario;
    }

    /** Un consultorio nuevo en Buenos Aires, con el odontólogo y atención de lunes a viernes de 09:00 a 13:00. */
    public static AgendaDePrueba nueva(JdbcTemplate jdbc) {
        CuentasDePrueba.Cuenta cuenta = CuentasDePrueba.crear(jdbc);
        AgendaDePrueba agenda = new AgendaDePrueba(jdbc, cuenta.consultorio(), cuenta.usuario());
        for (int dia = 1; dia <= 5; dia++) {
            agenda.franja(dia, "09:00", "13:00");
        }
        return agenda;
    }

    public UUID consultorio() {
        return consultorio;
    }

    public UUID usuario() {
        return usuario;
    }

    /** Instante del lunes de prueba a esa hora local ({@code "10:00"}). */
    public static Instant lunesA(String hora) {
        String[] partes = hora.split(":");
        return LUNES.plusSeconds(Integer.parseInt(partes[0]) * 3600L + Integer.parseInt(partes[1]) * 60L);
    }

    public AgendaDePrueba franja(int dia, String inicio, String fin) {
        jdbc.update("""
                INSERT INTO disponibilidad (id, consultorio_id, dia_semana, hora_inicio, hora_fin)
                VALUES (?, ?, ?, CAST(? AS time), CAST(? AS time))""", UUID.randomUUID(), consultorio, dia, inicio, fin);
        return this;
    }

    public UUID prestacion(String nombre, int duracionMin, boolean activa) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tipo_prestacion (id, consultorio_id, nombre, duracion_min, activa, orden)
                VALUES (?, ?, ?, ?, ?, 1)""", id, consultorio, nombre, duracionMin, activa);
        return id;
    }

    public UUID paciente(String nombre, String apellido) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO paciente (id, consultorio_id, nombre, apellido, dni, telefono_e164, origen)
                VALUES (?, ?, ?, ?, ?, '+5491100000001', 'MANUAL')""", id, consultorio, nombre, apellido, dniNuevo());
        return id;
    }

    public UUID turno(UUID paciente, UUID prestacion, Instant inicio, int duracionMin, String estado,
            boolean sobreturno) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO turno (id, consultorio_id, paciente_id, tipo_prestacion_id, inicio, fin, duracion_min,
                                   estado, es_sobreturno, origen, cancelado_por)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'MANUAL', ?)""", id, consultorio, paciente, prestacion,
                java.sql.Timestamp.from(inicio), java.sql.Timestamp.from(inicio.plusSeconds(duracionMin * 60L)),
                duracionMin, estado, sobreturno, "CANCELADO".equals(estado) ? "ODONTOLOGO" : null);
        return id;
    }

    public UUID bloqueo(Instant inicio, Instant fin, String motivo) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO bloqueo (id, consultorio_id, inicio, fin, motivo) VALUES (?, ?, ?, ?, ?)", id,
                consultorio, java.sql.Timestamp.from(inicio), java.sql.Timestamp.from(fin), motivo);
        return id;
    }

    /** Un DNI ficticio distinto en cada llamada: {@code 9} más 7 dígitos. */
    public static String dniNuevo() {
        return "9" + String.format("%07d", DNI.getAndIncrement() % 10_000_000);
    }
}
