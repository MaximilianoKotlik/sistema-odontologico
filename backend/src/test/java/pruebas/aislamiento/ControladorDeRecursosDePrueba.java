package pruebas.aislamiento;

import ar.sistemaodontologico.compartido.application.TenantContext;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import ar.sistemaodontologico.compartido.domain.GeneradorDeIds;
import ar.sistemaodontologico.compartido.domain.error.RecursoNoEncontrado;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Solo de test (C-02, design D8): endpoints de ejemplo que leen el tenant del {@link TenantContext} y nunca
 * de la petición. Se carga con {@code @Import} solo en {@code CompartidoAislamientoTest}.
 */
@RestController
public class ControladorDeRecursosDePrueba {

    /** El body trae un consultorio_id a propósito: el controlador debe ignorarlo. */
    public record AltaDeRecurso(UUID consultorio_id, String nombre) {
    }

    public record AltaDeConsultorio(String slug) {
    }

    private final TenantContext tenant;
    private final RepositorioDeRecursosDePrueba recursos;
    private final GeneradorDeIds ids;
    private final JdbcTemplate jdbc;

    public ControladorDeRecursosDePrueba(TenantContext tenant, RepositorioDeRecursosDePrueba recursos,
            GeneradorDeIds ids, JdbcTemplate jdbc) {
        this.tenant = tenant;
        this.recursos = recursos;
        this.ids = ids;
        this.jdbc = jdbc;
    }

    @GetMapping("/api/v1/pruebas/recursos/{id}")
    public RepositorioDeRecursosDePrueba.Recurso buscar(@PathVariable UUID id) {
        return recursos.buscar(tenant.actual(), id).orElseThrow(() -> new RecursoNoEncontrado("recurso"));
    }

    @GetMapping("/api/v1/pruebas/recursos")
    public List<RepositorioDeRecursosDePrueba.Recurso> listar() {
        return recursos.listar(tenant.actual());
    }

    @PostMapping("/api/v1/pruebas/recursos")
    @ResponseStatus(HttpStatus.CREATED)
    public RepositorioDeRecursosDePrueba.Recurso crear(@RequestBody AltaDeRecurso alta) {
        ConsultorioId consultorio = tenant.actual();
        UUID id = ids.nuevo();
        recursos.guardar(consultorio, id, alta.nombre());
        return recursos.buscar(consultorio, id).orElseThrow();
    }

    /** Alta directa para probar el mapeo de uk_consultorio_slug → 409 SLUG_EN_USO (tarea 4.2). */
    @PostMapping("/api/v1/pruebas/consultorios")
    @ResponseStatus(HttpStatus.CREATED)
    public void crearConsultorio(@RequestBody AltaDeConsultorio alta) {
        jdbc.update("INSERT INTO consultorio (id, nombre, slug, plan) VALUES (?, 'Consultorio Demo', ?, 'PRUEBA')",
                ids.nuevo(), alta.slug());
    }
}
