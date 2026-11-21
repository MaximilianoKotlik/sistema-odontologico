package ar.sistemaodontologico.consultorio.infrastructure;

import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import ar.sistemaodontologico.consultorio.application.PrestacionRepositorio;
import ar.sistemaodontologico.consultorio.domain.TipoDePrestacion;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Tipos de prestación con JdbcClient, como el resto del módulo (design D2 preveía JPA; se mantuvo JDBC para no
 * mezclar dos estilos de persistencia en {@code consultorio}). Toda consulta filtra por {@code consultorio_id}.
 */
@Repository
class PrestacionRepositorioJdbc implements PrestacionRepositorio {

    private static final String COLUMNAS = "id, nombre, duracion_min, activa, visible_publica, orden";

    private final JdbcClient jdbc;

    PrestacionRepositorioJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<TipoDePrestacion> listar(ConsultorioId consultorio, boolean incluirInactivas) {
        return jdbc.sql("SELECT " + COLUMNAS + " FROM tipo_prestacion WHERE consultorio_id = :consultorio"
                        + (incluirInactivas ? "" : " AND activa") + " ORDER BY orden, lower(nombre)")
                .param("consultorio", consultorio.valor())
                .query(PrestacionRepositorioJdbc::mapear)
                .list();
    }

    @Override
    public Optional<TipoDePrestacion> buscar(ConsultorioId consultorio, UUID id) {
        return jdbc.sql("SELECT " + COLUMNAS + " FROM tipo_prestacion WHERE consultorio_id = :consultorio AND id = :id")
                .param("consultorio", consultorio.valor())
                .param("id", id)
                .query(PrestacionRepositorioJdbc::mapear)
                .optional();
    }

    @Override
    public boolean nombreEnUso(ConsultorioId consultorio, String nombre, UUID excepto) {
        return jdbc.sql("""
                SELECT count(*) FROM tipo_prestacion
                WHERE consultorio_id = :consultorio AND lower(nombre) = lower(:nombre) AND id <> :excepto""")
                .param("consultorio", consultorio.valor())
                .param("nombre", nombre)
                .param("excepto", excepto)
                .query(Long.class)
                .single() > 0;
    }

    @Override
    public int siguienteOrden(ConsultorioId consultorio) {
        return jdbc.sql("SELECT coalesce(max(orden), 0) + 1 FROM tipo_prestacion WHERE consultorio_id = :consultorio")
                .param("consultorio", consultorio.valor())
                .query(Integer.class)
                .single();
    }

    @Override
    public void guardarNueva(ConsultorioId consultorio, TipoDePrestacion prestacion) {
        jdbc.sql("""
                INSERT INTO tipo_prestacion (id, consultorio_id, nombre, duracion_min, activa, visible_publica, orden)
                VALUES (:id, :consultorio, :nombre, :duracion, :activa, :visible, :orden)""")
                .param("id", prestacion.id())
                .param("consultorio", consultorio.valor())
                .param("nombre", prestacion.nombre())
                .param("duracion", prestacion.duracionMin())
                .param("activa", prestacion.activa())
                .param("visible", prestacion.visiblePublica())
                .param("orden", prestacion.orden())
                .update();
    }

    @Override
    public void actualizar(ConsultorioId consultorio, TipoDePrestacion prestacion) {
        jdbc.sql("""
                UPDATE tipo_prestacion
                SET nombre = :nombre, duracion_min = :duracion, activa = :activa, visible_publica = :visible,
                    orden = :orden, updated_at = now()
                WHERE consultorio_id = :consultorio AND id = :id""")
                .param("nombre", prestacion.nombre())
                .param("duracion", prestacion.duracionMin())
                .param("activa", prestacion.activa())
                .param("visible", prestacion.visiblePublica())
                .param("orden", prestacion.orden())
                .param("consultorio", consultorio.valor())
                .param("id", prestacion.id())
                .update();
    }

    private static TipoDePrestacion mapear(ResultSet fila, int numero) throws SQLException {
        return new TipoDePrestacion(fila.getObject("id", UUID.class), fila.getString("nombre"),
                fila.getInt("duracion_min"), fila.getBoolean("activa"), fila.getBoolean("visible_publica"),
                fila.getInt("orden"));
    }
}
