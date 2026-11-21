package ar.sistemaodontologico.consultorio.infrastructure;

import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import ar.sistemaodontologico.compartido.domain.GeneradorDeIds;
import ar.sistemaodontologico.consultorio.application.DisponibilidadRepositorio;
import ar.sistemaodontologico.consultorio.domain.Franja;
import ar.sistemaodontologico.consultorio.domain.GrillaSemanal;
import java.time.LocalTime;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Grilla semanal con JdbcClient (design D2): el reemplazo completo es un {@code DELETE} más un {@code INSERT} por
 * franja, y la exclusión {@code ex_disponibilidad_sin_solape} actúa fila por fila.
 */
@Repository
class DisponibilidadRepositorioJdbc implements DisponibilidadRepositorio {

    private final JdbcClient jdbc;
    private final GeneradorDeIds ids;

    DisponibilidadRepositorioJdbc(JdbcClient jdbc, GeneradorDeIds ids) {
        this.jdbc = jdbc;
        this.ids = ids;
    }

    @Override
    public GrillaSemanal consultar(ConsultorioId consultorio) {
        return new GrillaSemanal(jdbc.sql("""
                        SELECT dia_semana, hora_inicio, hora_fin FROM disponibilidad
                        WHERE consultorio_id = :consultorio ORDER BY dia_semana, hora_inicio""")
                .param("consultorio", consultorio.valor())
                .query((fila, numero) -> new Franja(fila.getInt("dia_semana"),
                        fila.getObject("hora_inicio", LocalTime.class), fila.getObject("hora_fin", LocalTime.class)))
                .list());
    }

    @Override
    public void bloquear(ConsultorioId consultorio) {
        jdbc.sql("SELECT 1 FROM consultorio WHERE id = :consultorio FOR UPDATE")
                .param("consultorio", consultorio.valor())
                .query(Integer.class)
                .list();
    }

    @Override
    public void reemplazar(ConsultorioId consultorio, GrillaSemanal grilla) {
        // Ya bloqueada si el caso de uso llamó a bloquear(); en la misma transacción, repetirlo no espera.
        bloquear(consultorio);
        jdbc.sql("DELETE FROM disponibilidad WHERE consultorio_id = :consultorio")
                .param("consultorio", consultorio.valor())
                .update();
        for (Franja franja : grilla.franjas()) {
            jdbc.sql("""
                    INSERT INTO disponibilidad (id, consultorio_id, dia_semana, hora_inicio, hora_fin)
                    VALUES (:id, :consultorio, :dia, :inicio, :fin)""")
                    .param("id", ids.nuevo())
                    .param("consultorio", consultorio.valor())
                    .param("dia", franja.diaSemana())
                    .param("inicio", franja.inicio())
                    .param("fin", franja.fin())
                    .update();
        }
    }
}
