package ar.sistemaodontologico.auditoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.sistemaodontologico.compartido.IntegracionPostgresTest;
import ar.sistemaodontologico.compartido.application.PublicadorDeAuditoria;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import ar.sistemaodontologico.compartido.domain.auditoria.Actor;
import ar.sistemaodontologico.compartido.domain.auditoria.ActorTipo;
import ar.sistemaodontologico.compartido.domain.auditoria.CambioAuditado;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Persistencia de los eventos publicados por el puerto de C-02 (C-03, tareas 2.2 a 2.4 y 2.6). Spec auditoria —
 * Requirements: Persistencia del evento publicado, Atomicidad entre el evento y el cambio auditado y Pertenencia
 * a un consultorio.
 */
@Import(RegistradorDeAuditoriaTest.Configuracion.class)
class RegistradorDeAuditoriaTest extends IntegracionPostgresTest {

    static final Instant AHORA = Instant.parse("2026-12-01T13:00:00Z");

    @TestConfiguration
    static class Configuracion {

        @Bean
        @Primary
        Clock relojFijo() {
            return Clock.fixed(AHORA, ZoneOffset.UTC);
        }
    }

    @Autowired
    private PublicadorDeAuditoria publicador;

    @Autowired
    private TransactionTemplate transaccion;

    @Autowired
    private JdbcTemplate jdbc;

    private ConsultorioId consultorio;

    @BeforeEach
    void crearConsultorio() {
        consultorio = new ConsultorioId(insertarConsultorio());
    }

    @Test
    void persisteElEventoCompletoConValoresAnidados() {
        UUID odontologo = UUID.randomUUID();
        UUID turno = UUID.randomUUID();
        CambioAuditado cambio = new CambioAuditado(consultorio, new Actor(ActorTipo.ODONTOLOGO, odontologo), "turno",
                turno, "TURNO_REPROGRAMADO", Map.of("inicio", "2026-12-02T12:00:00Z"),
                Map.of("inicio", "2026-12-03T12:00:00Z", "paciente", Map.of("paciente_id", "p-1")), "203.0.113.7");

        transaccion.executeWithoutResult(estado -> publicador.publicar(cambio));

        Map<String, Object> fila = unicoEventoDe(consultorio);
        assertThat(fila)
                .containsEntry("actor_tipo", "ODONTOLOGO")
                .containsEntry("actor_id", odontologo)
                .containsEntry("entidad", "turno")
                .containsEntry("entidad_id", turno)
                .containsEntry("accion", "TURNO_REPROGRAMADO")
                .containsEntry("ip", "203.0.113.7");
        assertThat(((Timestamp) fila.get("ocurrido_at")).toInstant()).isEqualTo(AHORA);
        assertThat((String) fila.get("valor_anterior")).isEqualTo("{\"inicio\": \"2026-12-02T12:00:00Z\"}");
        assertThat((String) fila.get("valor_nuevo"))
                .contains("\"inicio\": \"2026-12-03T12:00:00Z\"")
                .contains("\"paciente\": {\"paciente_id\": \"p-1\"}");
        assertThat((UUID) fila.get("id")).isNotNull();
    }

    @Test
    void persisteUnEventoDeSistemaSinActorNiValorAnteriorNiIp() {
        transaccion.executeWithoutResult(estado -> publicador.publicar(new CambioAuditado(consultorio, Actor.sistema(),
                "consultorio", consultorio.valor(), "PLAN_VENCIDO", null, Map.of("plan", "VENCIDO"), null)));

        assertThat(unicoEventoDe(consultorio))
                .containsEntry("actor_tipo", "SISTEMA")
                .containsEntry("actor_id", null)
                .containsEntry("valor_anterior", null)
                .containsEntry("ip", null);
    }

    @Test
    void siLaTransaccionSeRevierteNoQuedaEvento() {
        transaccion.executeWithoutResult(estado -> {
            publicador.publicar(cambioDe(consultorio, null));
            estado.setRollbackOnly();
        });

        assertThat(eventosDe(consultorio)).isEmpty();
    }

    @Test
    void siFallaLaAuditoriaElCambioSeRevierte() {
        // El caso de uso da de alta un consultorio y publica un evento de otro consultorio que no existe:
        // la FK del evento falla antes del commit y el alta tampoco queda (RN-AD-03).
        UUID nuevo = UUID.randomUUID();
        ConsultorioId inexistente = new ConsultorioId(UUID.randomUUID());

        assertThatThrownBy(() -> transaccion.executeWithoutResult(estado -> {
            jdbc.update("INSERT INTO consultorio (id, nombre, slug, plan) VALUES (?, 'Consultorio Demo', ?, 'PRUEBA')",
                    nuevo, "revertido-" + nuevo.toString().substring(0, 8));
            publicador.publicar(cambioDe(inexistente, null));
        })).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM consultorio WHERE id = ?", Integer.class, nuevo))
                .isZero();
    }

    @Test
    void cadaEventoQuedaSoloBajoSuConsultorio() {
        ConsultorioId otro = new ConsultorioId(insertarConsultorio());

        transaccion.executeWithoutResult(estado -> {
            publicador.publicar(cambioDe(consultorio, null));
            publicador.publicar(cambioDe(otro, null));
        });

        assertThat(eventosDe(consultorio)).hasSize(1);
        assertThat(eventosDe(otro)).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evento_auditoria WHERE consultorio_id IN (?, ?)",
                Integer.class, consultorio.valor(), otro.valor())).isEqualTo(2);
    }

    @Test
    void unaIpInvalidaSeGuardaVaciaSinRevertirElCambio() {
        transaccion.executeWithoutResult(estado -> publicador.publicar(cambioDe(consultorio, "no-es-una-ip")));

        assertThat(unicoEventoDe(consultorio)).containsEntry("ip", null);
    }

    private CambioAuditado cambioDe(ConsultorioId deConsultorio, String ip) {
        return new CambioAuditado(deConsultorio, Actor.sistema(), "consultorio", deConsultorio.valor(),
                "CONSULTORIO_CONFIG_CAMBIADA", Map.of("paso_huecos_min", 15), Map.of("paso_huecos_min", 30), ip);
    }

    private Map<String, Object> unicoEventoDe(ConsultorioId deConsultorio) {
        List<Map<String, Object>> eventos = eventosDe(deConsultorio);
        assertThat(eventos).hasSize(1);
        return eventos.get(0);
    }

    private List<Map<String, Object>> eventosDe(ConsultorioId deConsultorio) {
        return jdbc.queryForList("""
                SELECT id, ocurrido_at, actor_tipo, actor_id, entidad, entidad_id, accion,
                    valor_anterior::text AS valor_anterior, valor_nuevo::text AS valor_nuevo, host(ip) AS ip
                FROM evento_auditoria WHERE consultorio_id = ?
                """, deConsultorio.valor());
    }

    private UUID insertarConsultorio() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO consultorio (id, nombre, slug, plan) VALUES (?, 'Consultorio Demo', ?, 'PRUEBA')",
                id, "registro-" + id.toString().substring(0, 8));
        return id;
    }
}
