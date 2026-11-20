package ar.sistemaodontologico.auditoria;

import static org.assertj.core.api.Assertions.assertThat;

import ar.sistemaodontologico.compartido.IntegracionPostgresTest;
import ar.sistemaodontologico.compartido.application.PublicadorDeAuditoria;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import ar.sistemaodontologico.compartido.domain.auditoria.Actor;
import ar.sistemaodontologico.compartido.domain.auditoria.CambioAuditado;
import jakarta.persistence.EntityManager;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import pruebas.auditoria.ConsultorioJpaDePrueba;

/**
 * El evento puede referenciar filas que el caso de uso creó con JPA en la misma transacción (C-03, tarea 2.5;
 * design D-3): el listener corre antes del flush de Hibernate, así que el adaptador hace flush antes de insertar.
 * Spec auditoria — Scenario: Evento que referencia una fila creada en la misma transacción.
 */
@Import(AuditoriaConCambiosJpaTest.Configuracion.class)
class AuditoriaConCambiosJpaTest extends IntegracionPostgresTest {

    /** Registra la entidad de prueba solo en este contexto, junto con las de producción. */
    @TestConfiguration
    @EntityScan(basePackages = {"ar.sistemaodontologico", "pruebas.auditoria"})
    static class Configuracion {
    }

    @Autowired
    private EntityManager entidades;

    @Autowired
    private PublicadorDeAuditoria publicador;

    @Autowired
    private TransactionTemplate transaccion;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void elEventoDeUnConsultorioCreadoConJpaEnLaMismaTransaccionSePersiste() {
        UUID id = UUID.randomUUID();

        transaccion.executeWithoutResult(estado -> {
            entidades.persist(new ConsultorioJpaDePrueba(id, "jpa-" + id.toString().substring(0, 8)));
            publicador.publicar(new CambioAuditado(new ConsultorioId(id), Actor.sistema(), "consultorio", id,
                    "CUENTA_CREADA", null, Map.of("plan", "PRUEBA"), null));
        });

        assertThat(jdbc.queryForObject("SELECT count(*) FROM consultorio WHERE id = ?", Integer.class, id))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT accion FROM evento_auditoria WHERE consultorio_id = ?",
                String.class, id)).isEqualTo("CUENTA_CREADA");
    }
}
