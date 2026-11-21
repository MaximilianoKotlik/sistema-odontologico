package ar.sistemaodontologico.auditoria.domain;

import static org.assertj.core.api.Assertions.assertThat;

import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import ar.sistemaodontologico.compartido.domain.auditoria.Actor;
import ar.sistemaodontologico.compartido.domain.auditoria.ActorTipo;
import ar.sistemaodontologico.compartido.domain.auditoria.CambioAuditado;
import ar.sistemaodontologico.compartido.domain.auditoria.EventoDeAuditoria;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Mapeo del evento publicado al registro persistible (C-03, tarea 2.1; design D-4). */
class RegistroDeAuditoriaTest {

    private static final Instant OCURRIDO = Instant.parse("2026-12-01T13:00:00Z");
    private static final UUID ID = UUID.fromString("0193a1b2-0000-7000-8000-000000000001");

    private final ConsultorioId consultorio = new ConsultorioId(UUID.randomUUID());

    @Test
    void copiaTodosLosDatosDelEventoConElIdRecibido() {
        UUID odontologo = UUID.randomUUID();
        UUID turno = UUID.randomUUID();
        CambioAuditado cambio = new CambioAuditado(consultorio, new Actor(ActorTipo.ODONTOLOGO, odontologo), "turno",
                turno, "TURNO_REPROGRAMADO", Map.of("inicio", "2026-12-02T12:00:00Z"),
                Map.of("inicio", "2026-12-03T12:00:00Z"), "203.0.113.7");

        RegistroDeAuditoria registro = RegistroDeAuditoria.desde(new EventoDeAuditoria(OCURRIDO, cambio), ID);

        assertThat(registro.id()).isEqualTo(ID);
        assertThat(registro.consultorioId()).isEqualTo(consultorio.valor());
        assertThat(registro.ocurridoAt()).isEqualTo(OCURRIDO);
        assertThat(registro.actorTipo()).isEqualTo(ActorTipo.ODONTOLOGO);
        assertThat(registro.actorId()).isEqualTo(odontologo);
        assertThat(registro.entidad()).isEqualTo("turno");
        assertThat(registro.entidadId()).isEqualTo(turno);
        assertThat(registro.accion()).isEqualTo("TURNO_REPROGRAMADO");
        assertThat(registro.valorAnterior()).containsEntry("inicio", "2026-12-02T12:00:00Z");
        assertThat(registro.valorNuevo()).containsEntry("inicio", "2026-12-03T12:00:00Z");
        assertThat(registro.ip()).isEqualTo("203.0.113.7");
    }

    @Test
    void eventoDeSistemaSinActorNiValorAnteriorNiIp() {
        CambioAuditado cambio = new CambioAuditado(consultorio, Actor.sistema(), "consultorio", consultorio.valor(),
                "PLAN_VENCIDO", null, Map.of("plan", "VENCIDO"), null);

        RegistroDeAuditoria registro = RegistroDeAuditoria.desde(new EventoDeAuditoria(OCURRIDO, cambio), ID);

        assertThat(registro.actorTipo()).isEqualTo(ActorTipo.SISTEMA);
        assertThat(registro.actorId()).isNull();
        assertThat(registro.valorAnterior()).isNull();
        assertThat(registro.ip()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"203.0.113.7", "::1", "2001:db8::42", "::ffff:192.0.2.10"})
    void conservaLasIpLiterales(String ip) {
        assertThat(registroConIp(ip).ip()).isEqualTo(ip);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "no-es-ip", "localhost", "consultorio.example.com", "999.1.1.1",
            "203.0.113", "203.0.113.7.1", "2001:db8::zz", "203.0.113.7, 198.51.100.2"})
    void unaIpInvalidaQuedaVaciaSinResolverNombres(String ip) {
        // "localhost" y los nombres de host también quedan vacíos: no se resuelven por DNS (design D-4).
        assertThat(registroConIp(ip).ip()).isNull();
    }

    private RegistroDeAuditoria registroConIp(String ip) {
        CambioAuditado cambio = new CambioAuditado(consultorio, Actor.sistema(), "consultorio", consultorio.valor(),
                "CONSULTORIO_CONFIG_CAMBIADA", null, Map.of("paso_huecos_min", 30), ip);
        return RegistroDeAuditoria.desde(new EventoDeAuditoria(OCURRIDO, cambio), ID);
    }
}
