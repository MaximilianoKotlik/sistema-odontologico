package ar.sistemaodontologico.compartido.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.sistemaodontologico.compartido.IntegracionPostgresTest;
import ar.sistemaodontologico.compartido.application.PublicadorDeAuditoria;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import ar.sistemaodontologico.compartido.domain.auditoria.Actor;
import ar.sistemaodontologico.compartido.domain.auditoria.ActorTipo;
import ar.sistemaodontologico.compartido.domain.auditoria.CambioAuditado;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import pruebas.auditoria.SuscriptorDeAuditoriaDePrueba;

/** Spec auditoria — Requirement: Publicación dentro de la transacción del cambio (C-02, tarea 5.2). */
@Import(PublicadorDeAuditoriaSpringTest.Configuracion.class)
class PublicadorDeAuditoriaSpringTest extends IntegracionPostgresTest {

    static final Instant AHORA = Instant.parse("2026-12-01T13:00:00Z");

    @TestConfiguration
    static class Configuracion {

        @Bean
        @Primary
        Clock relojFijo() {
            return Clock.fixed(AHORA, ZoneOffset.UTC);
        }

        @Bean
        SuscriptorDeAuditoriaDePrueba suscriptorDeAuditoriaDePrueba() {
            return new SuscriptorDeAuditoriaDePrueba();
        }
    }

    @Autowired
    private PublicadorDeAuditoria publicador;

    @Autowired
    private SuscriptorDeAuditoriaDePrueba suscriptor;

    @Autowired
    private TransactionTemplate transaccion;

    private final ConsultorioId consultorio = new ConsultorioId(UUID.randomUUID());

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void limpiar() {
        suscriptor.limpiar();
        // Desde C-03 el evento se persiste de verdad, y evento_auditoria tiene FK a consultorio.
        jdbc.update("INSERT INTO consultorio (id, nombre, slug, plan) VALUES (?, 'Consultorio Demo', ?, 'PRUEBA')",
                consultorio.valor(), "publicador-" + consultorio.valor().toString().substring(0, 8));
    }

    @Test
    void elSuscriptorRecibeElEventoConElInstanteDelReloj() {
        UUID odontologo = UUID.randomUUID();

        transaccion.executeWithoutResult(estado -> publicador.publicar(cambio(new Actor(ActorTipo.ODONTOLOGO, odontologo))));

        assertThat(suscriptor.confirmados()).singleElement().satisfies(evento -> {
            assertThat(evento.ocurridoAt()).isEqualTo(AHORA);
            assertThat(evento.cambio().consultorio()).isEqualTo(consultorio);
            assertThat(evento.cambio().actor().id()).isEqualTo(odontologo);
            assertThat(evento.cambio().accion()).isEqualTo("CONSULTORIO_CONFIG_CAMBIADA");
        });
    }

    @Test
    void siLaTransaccionSeRevierteElEventoNoSeConfirma() {
        transaccion.executeWithoutResult(estado -> {
            publicador.publicar(cambio(Actor.sistema()));
            estado.setRollbackOnly();
        });

        assertThat(suscriptor.confirmados()).isEmpty();
    }

    @Test
    void publicarFueraDeUnaTransaccionFalla() {
        assertThatThrownBy(() -> publicador.publicar(cambio(Actor.sistema())))
                .isInstanceOf(IllegalStateException.class);
        assertThat(suscriptor.confirmados()).isEmpty();
    }

    private CambioAuditado cambio(Actor actor) {
        return new CambioAuditado(consultorio, actor, "consultorio", consultorio.valor(), "CONSULTORIO_CONFIG_CAMBIADA",
                Map.of("paso_huecos_min", 15), Map.of("paso_huecos_min", 30), null);
    }
}
