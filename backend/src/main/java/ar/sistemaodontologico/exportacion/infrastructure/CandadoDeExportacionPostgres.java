package ar.sistemaodontologico.exportacion.infrastructure;

import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import ar.sistemaodontologico.exportacion.application.CandadoDeExportacion;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** {@code pg_try_advisory_xact_lock} sobre el consultorio: se libera solo al terminar la transacción (design D7). */
@Component
class CandadoDeExportacionPostgres implements CandadoDeExportacion {

    private final JdbcClient jdbc;

    CandadoDeExportacionPostgres(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean tomar(ConsultorioId consultorio) {
        return jdbc.sql("SELECT pg_try_advisory_xact_lock(hashtextextended(:clave, 0))")
                .param("clave", "exportacion:" + consultorio.valor())
                .query(Boolean.class)
                .single();
    }
}
