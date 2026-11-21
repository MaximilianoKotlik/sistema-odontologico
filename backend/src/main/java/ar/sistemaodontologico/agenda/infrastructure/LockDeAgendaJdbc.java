package ar.sistemaodontologico.agenda.infrastructure;

import ar.sistemaodontologico.agenda.application.LockDeAgenda;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** El mismo {@code SELECT … FOR UPDATE} que {@code DisponibilidadRepositorio.bloquear} de C-08 (design D3). */
@Component
class LockDeAgendaJdbc implements LockDeAgenda {

    private final JdbcClient jdbc;

    LockDeAgendaJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void tomar(ConsultorioId consultorio) {
        jdbc.sql("SELECT 1 FROM consultorio WHERE id = :consultorio FOR UPDATE")
                .param("consultorio", consultorio.valor())
                .query(Integer.class)
                .list();
    }
}
