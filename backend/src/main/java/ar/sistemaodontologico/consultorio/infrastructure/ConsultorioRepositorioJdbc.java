package ar.sistemaodontologico.consultorio.infrastructure;

import ar.sistemaodontologico.consultorio.application.ConsultorioRepositorio;
import ar.sistemaodontologico.consultorio.domain.NuevoConsultorio;
import java.util.HashSet;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Consultorio con JdbcClient: inserciones puntuales, sin entidad JPA (C-04). */
@Repository
class ConsultorioRepositorioJdbc implements ConsultorioRepositorio {

    private final JdbcClient jdbc;

    ConsultorioRepositorioJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void guardarNuevo(NuevoConsultorio consultorio) {
        jdbc.sql("""
                INSERT INTO consultorio (id, nombre, slug, plan, prueba_hasta)
                VALUES (:id, :nombre, :slug, 'PRUEBA', :pruebaHasta)""")
                .param("id", consultorio.id().valor())
                .param("nombre", consultorio.nombre())
                .param("slug", consultorio.slug())
                .param("pruebaHasta", consultorio.pruebaHasta())
                .update();
    }

    @Override
    public Set<String> empiezanCon(String prefijo) {
        // El slug solo admite [a-z0-9-], así que no hay comodines de LIKE que escapar.
        return new HashSet<>(jdbc.sql("SELECT slug FROM consultorio WHERE slug LIKE :prefijo")
                .param("prefijo", prefijo + "%")
                .query(String.class)
                .list());
    }
}
