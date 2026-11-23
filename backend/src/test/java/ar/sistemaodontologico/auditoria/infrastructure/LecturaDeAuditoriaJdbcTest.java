package ar.sistemaodontologico.auditoria.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import ar.sistemaodontologico.auditoria.application.LecturaDeAuditoria;
import ar.sistemaodontologico.auditoria.application.LecturaDeAuditoria.EventoLeido;
import ar.sistemaodontologico.compartido.IntegracionPostgresTest;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Actor de una reserva con un DNI en revisión (C-18, tarea 3.2b, design D3; decisión de Maxi en #51). */
class LecturaDeAuditoriaJdbcTest extends IntegracionPostgresTest {

    private static final Instant INICIO = Instant.parse("2030-03-04T13:00:00Z");

    @Autowired
    private LecturaDeAuditoria lectura;

    @Autowired
    private JdbcClient jdbc;

    private UUID consultorio;
    private UUID prestacion;
    private UUID paciente;
    private UUID turnoDeOrigen;
    private UUID otroTurno;

    @BeforeEach
    void datos() {
        consultorio = consultorio();
        prestacion = prestacion(consultorio);
        paciente = paciente(consultorio, "10000011");
        turnoDeOrigen = turno(consultorio, paciente, prestacion, INICIO);
        otroTurno = turno(consultorio, paciente, prestacion, INICIO.plusSeconds(3600));
        evento(consultorio, "PACIENTE", paciente, turnoDeOrigen, INICIO);
        evento(consultorio, "PACIENTE", paciente, otroTurno, INICIO.plusSeconds(1));
    }

    @Test
    void revisionPendienteMarcaElActorYNoTraeSuNombre() {
        marcarRevision(paciente, turnoDeOrigen);

        EventoLeido evento = unico(turnoDeOrigen);

        assertThat(evento.actor().tipo()).isEqualTo("PACIENTE");
        assertThat(evento.actor().identidadEnRevision()).isTrue();
        assertThat(evento.actor().nombre()).isNull();
    }

    @Test
    void elContextoSigueIndicandoElTurnoYElPacienteAsignado() {
        marcarRevision(paciente, turnoDeOrigen);

        EventoLeido evento = unico(turnoDeOrigen);

        assertThat(evento.contexto().inicio()).isEqualTo(INICIO);
        assertThat(evento.contexto().paciente()).isEqualTo("Paciente Prueba");
    }

    @Test
    void revisionResueltaMuestraElNombreDeLaFicha() {
        marcarRevision(paciente, turnoDeOrigen);
        jdbc.sql("""
                UPDATE paciente SET requiere_revision = false, nombre_informado = NULL, apellido_informado = NULL,
                       email_informado = NULL, informado_at = NULL, informado_turno_id = NULL
                 WHERE id = ?""").params(paciente).update();

        EventoLeido evento = unico(turnoDeOrigen);

        assertThat(evento.actor().identidadEnRevision()).isFalse();
        assertThat(evento.actor().nombre()).isEqualTo("Paciente Prueba");
    }

    @Test
    void otroTurnoDelMismoPacienteNoSeMarca() {
        marcarRevision(paciente, turnoDeOrigen);

        EventoLeido evento = unico(otroTurno);

        assertThat(evento.actor().identidadEnRevision()).isFalse();
        assertThat(evento.actor().nombre()).isEqualTo("Paciente Prueba");
    }

    @Test
    void unEventoDelOdontologoEnElTurnoDeOrigenNoSeMarca() {
        marcarRevision(paciente, turnoDeOrigen);
        UUID odontologo = usuario(consultorio);
        evento(consultorio, "ODONTOLOGO", odontologo, turnoDeOrigen, INICIO.plusSeconds(2));

        List<EventoLeido> eventos = lectura.listarDeTurno(new ConsultorioId(consultorio), turnoDeOrigen);

        assertThat(eventos).hasSize(2);
        EventoLeido delOdontologo = eventos.get(1);
        assertThat(delOdontologo.actor().tipo()).isEqualTo("ODONTOLOGO");
        assertThat(delOdontologo.actor().identidadEnRevision()).isFalse();
        assertThat(delOdontologo.actor().nombre()).isEqualTo("Odonto Prueba");
    }

    @Test
    void unActorDeOtroConsultorioNoTraeNombre() {
        UUID otroConsultorio = consultorio();
        UUID ajeno = paciente(otroConsultorio, "10000012");
        evento(consultorio, "PACIENTE", ajeno, otroTurno, INICIO.plusSeconds(3));

        List<EventoLeido> eventos = lectura.listarDeTurno(new ConsultorioId(consultorio), otroTurno);

        assertThat(eventos.getLast().actor().nombre()).isNull();
        assertThat(eventos.getLast().actor().identidadEnRevision()).isFalse();
    }

    @Test
    void noLeeEventosDeOtroConsultorio() {
        UUID otroConsultorio = consultorio();

        assertThat(lectura.listarDeTurno(new ConsultorioId(otroConsultorio), turnoDeOrigen)).isEmpty();
    }

    private EventoLeido unico(UUID turno) {
        List<EventoLeido> eventos = lectura.listarDeTurno(new ConsultorioId(consultorio), turno);
        assertThat(eventos).hasSize(1);
        return eventos.getFirst();
    }

    private void marcarRevision(UUID idPaciente, UUID turno) {
        jdbc.sql("""
                UPDATE paciente SET requiere_revision = true, nombre_informado = 'Otro', apellido_informado = 'Nombre',
                       informado_at = ?, informado_turno_id = ?
                 WHERE id = ?""").params(Timestamp.from(INICIO), turno, idPaciente).update();
    }

    private UUID consultorio() {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO consultorio (id, nombre, slug, plan) VALUES (?, 'Consultorio de prueba', ?, 'PRUEBA')")
                .params(id, "aud-" + id.toString().substring(0, 8)).update();
        return id;
    }

    private UUID usuario(UUID idConsultorio) {
        UUID id = UUID.randomUUID();
        Timestamp at = Timestamp.from(INICIO);
        jdbc.sql("""
                INSERT INTO usuario (id, consultorio_id, email, password_hash, nombre, apellido, password_cambiado_at,
                                     terminos_version, terminos_aceptados_at)
                VALUES (?, ?, ?, 'hash-ficticio', 'Odonto', 'Prueba', ?, 'test', ?)""")
                .params(id, idConsultorio, "odontologo-" + id + "@example.com", at, at).update();
        return id;
    }

    private UUID prestacion(UUID idConsultorio) {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO tipo_prestacion (id, consultorio_id, nombre, duracion_min, orden) VALUES (?, ?, 'Consulta', 30, 1)")
                .params(id, idConsultorio).update();
        return id;
    }

    private UUID paciente(UUID idConsultorio, String dni) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO paciente (id, consultorio_id, nombre, apellido, dni, telefono_e164, origen)
                VALUES (?, ?, 'Paciente', 'Prueba', ?, '+5491100000001', 'MANUAL')""")
                .params(id, idConsultorio, dni).update();
        return id;
    }

    private UUID turno(UUID idConsultorio, UUID idPaciente, UUID idPrestacion, Instant inicio) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO turno (id, consultorio_id, paciente_id, tipo_prestacion_id, inicio, fin, duracion_min, estado, origen)
                VALUES (?, ?, ?, ?, ?, ?, 30, 'RESERVADO', 'LINK')""")
                .params(id, idConsultorio, idPaciente, idPrestacion, Timestamp.from(inicio),
                        Timestamp.from(inicio.plusSeconds(1800)))
                .update();
        return id;
    }

    private void evento(UUID idConsultorio, String actorTipo, UUID actorId, UUID turno, Instant ocurrido) {
        jdbc.sql("""
                INSERT INTO evento_auditoria (id, consultorio_id, ocurrido_at, actor_tipo, actor_id, entidad, entidad_id, accion)
                VALUES (?, ?, ?, ?, ?, 'turno', ?, 'TURNO_CREADO')""")
                .params(UUID.randomUUID(), idConsultorio, Timestamp.from(ocurrido), actorTipo, actorId, turno).update();
    }
}
