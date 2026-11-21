package ar.sistemaodontologico.agenda.application;

import static org.mockito.Mockito.when;

import ar.sistemaodontologico.compartido.IntegracionPostgresTest;
import ar.sistemaodontologico.compartido.application.PublicadorDeAuditoria;
import ar.sistemaodontologico.compartido.application.TenantContext;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import pruebas.agenda.AgendaDePrueba;
import pruebas.reloj.ConfiguracionDeRelojDePrueba;
import pruebas.reloj.RelojDePrueba;

/**
 * Base de los tests de los casos de uso de agenda (C-11). Los casos de uso corren sin petición HTTP: el consultorio
 * activo lo fija el test en un {@link TenantContext} simulado (C-12 y C-15 lo resuelven desde la sesión o el link).
 * Persistencia real con Testcontainers (regla dura 8); el publicador de auditoría es el real, espiado para simular
 * una falla.
 */
@Import(ConfiguracionDeRelojDePrueba.class)
abstract class CasoDeUsoDeAgendaTest extends IntegracionPostgresTest {

    /** Un evento de auditoría leído de la base, con los valores como JSON. */
    record Evento(String accion, String actorTipo, UUID actorId, String entidad, UUID entidadId, String anterior,
            String nuevo, String ip) {
    }

    @MockitoBean
    protected TenantContext tenant;

    @MockitoSpyBean
    protected PublicadorDeAuditoria auditoria;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected RelojDePrueba reloj;

    protected AgendaDePrueba agenda;
    protected ConsultorioId consultorio;
    protected Autor odontologo;

    @BeforeEach
    void crearAgendaYFijarTenant() {
        agenda = AgendaDePrueba.nueva(jdbc);
        consultorio = new ConsultorioId(agenda.consultorio());
        when(tenant.actual()).thenReturn(consultorio);
        when(tenant.estaResuelto()).thenReturn(true);
        odontologo = Autor.odontologo(agenda.usuario(), Optional.of("203.0.113.10"));
    }

    /** Los eventos del consultorio activo, en orden de registro. */
    protected List<Evento> eventos() {
        return jdbc.query("""
                SELECT accion, actor_tipo, actor_id, entidad, entidad_id, valor_anterior::text AS anterior,
                       valor_nuevo::text AS nuevo, host(ip) AS ip
                  FROM evento_auditoria WHERE consultorio_id = ? ORDER BY id""",
                (fila, numero) -> new Evento(fila.getString("accion"), fila.getString("actor_tipo"),
                        fila.getObject("actor_id", UUID.class), fila.getString("entidad"),
                        fila.getObject("entidad_id", UUID.class), fila.getString("anterior"), fila.getString("nuevo"),
                        fila.getString("ip")),
                consultorio.valor());
    }

    protected List<String> acciones() {
        return eventos().stream().map(Evento::accion).toList();
    }
}
