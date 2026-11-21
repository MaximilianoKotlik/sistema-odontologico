package ar.sistemaodontologico.consultorio.infrastructure;

import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import ar.sistemaodontologico.consultorio.application.ConsultorioRepositorio;
import ar.sistemaodontologico.consultorio.domain.ConfiguracionDelConsultorio;
import ar.sistemaodontologico.consultorio.domain.DatosDelConsultorio;
import ar.sistemaodontologico.consultorio.domain.NuevoConsultorio;
import ar.sistemaodontologico.consultorio.domain.ReglasDeReserva;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Consultorio con JdbcClient, sin entidad JPA (C-04). C-08 sigue igual en lugar de pasar a JPA (design D2): la
 * versión se controla en el {@code UPDATE} y el resto del módulo ya usa JDBC.
 */
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

    @Override
    public Optional<ConfiguracionDelConsultorio> buscarConfiguracion(ConsultorioId id) {
        return jdbc.sql("""
                SELECT nombre, slug, direccion, telefono_contacto, zona_horaria, anticipacion_cancelacion_min,
                       anticipacion_reserva_min, horizonte_reserva_dias, paso_huecos_min, reserva_publica_activa,
                       plan, prueba_hasta, version
                FROM consultorio WHERE id = :id""")
                .param("id", id.valor())
                .query((fila, numero) -> new ConfiguracionDelConsultorio(id,
                        new DatosDelConsultorio(fila.getString("nombre"), fila.getString("slug"),
                                fila.getString("direccion"), fila.getString("telefono_contacto"),
                                fila.getString("zona_horaria"),
                                new ReglasDeReserva(fila.getInt("anticipacion_cancelacion_min"),
                                        fila.getInt("anticipacion_reserva_min"), fila.getInt("horizonte_reserva_dias"),
                                        fila.getInt("paso_huecos_min")),
                                fila.getBoolean("reserva_publica_activa")),
                        fila.getString("plan"), fila.getObject("prueba_hasta", LocalDate.class),
                        fila.getLong("version")))
                .optional();
    }

    @Override
    public Optional<PlanGuardado> buscarPlan(ConsultorioId id) {
        return jdbc.sql("SELECT plan, prueba_hasta, zona_horaria FROM consultorio WHERE id = :id")
                .param("id", id.valor())
                .query((fila, numero) -> new PlanGuardado(fila.getString("plan"),
                        fila.getObject("prueba_hasta", LocalDate.class), fila.getString("zona_horaria")))
                .optional();
    }

    @Override
    public Optional<ConsultorioId> buscarIdPorSlug(String slug) {
        return jdbc.sql("SELECT id FROM consultorio WHERE slug = :slug")
                .param("slug", slug)
                .query((fila, numero) -> new ConsultorioId(fila.getObject("id", java.util.UUID.class)))
                .optional();
    }

    @Override
    public void actualizarDatos(ConsultorioId id, DatosDelConsultorio datos, long versionLeida) {
        int filas = jdbc.sql("""
                UPDATE consultorio
                SET nombre = :nombre, slug = :slug, direccion = :direccion, telefono_contacto = :telefono,
                    zona_horaria = :zona, anticipacion_cancelacion_min = :cancelacion,
                    anticipacion_reserva_min = :reserva, horizonte_reserva_dias = :horizonte, paso_huecos_min = :paso,
                    reserva_publica_activa = :reservaPublica, version = version + 1, updated_at = now()
                WHERE id = :id AND version = :version""")
                .param("nombre", datos.nombre())
                .param("slug", datos.slug())
                .param("direccion", datos.direccion())
                .param("telefono", datos.telefonoContacto())
                .param("zona", datos.zonaHoraria())
                .param("cancelacion", datos.reglas().anticipacionCancelacionMin())
                .param("reserva", datos.reglas().anticipacionReservaMin())
                .param("horizonte", datos.reglas().horizonteReservaDias())
                .param("paso", datos.reglas().pasoHuecosMin())
                .param("reservaPublica", datos.reservaPublicaActiva())
                .param("id", id.valor())
                .param("version", versionLeida)
                .update();
        if (filas == 0) {
            throw new OptimisticLockingFailureException("El consultorio cambió mientras se editaba");
        }
    }
}
