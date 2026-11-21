package pruebas.auditoria;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Solo de test (C-03, tarea 2.5): entidad JPA mínima sobre {@code consultorio}, para probar que el evento de
 * auditoría ve las filas que el caso de uso todavía tiene sin flush. Vive fuera de {@code ar.sistemaodontologico}
 * para que el escaneo de entidades de los demás tests no la levante (lo que pasó en C-01).
 */
@Entity
@Table(name = "consultorio")
public class ConsultorioJpaDePrueba {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private String slug;

    @Column(nullable = false)
    private String plan;

    protected ConsultorioJpaDePrueba() {
    }

    public ConsultorioJpaDePrueba(UUID id, String slug) {
        this.id = id;
        this.nombre = "Consultorio Demo";
        this.slug = slug;
        this.plan = "PRUEBA";
    }

    public UUID getId() {
        return id;
    }
}
