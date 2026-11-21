package pruebas.errores;

import ar.sistemaodontologico.compartido.application.TenantNoResueltoException;
import ar.sistemaodontologico.compartido.domain.error.ConflictoDeNegocio;
import ar.sistemaodontologico.compartido.domain.error.RecursoNoEncontrado;
import ar.sistemaodontologico.compartido.domain.error.ValidacionDeNegocio;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.Map;

/**
 * Controlador solo de test (C-02, design D8): vive fuera de {@code ar.sistemaodontologico} para no entrar en
 * el escaneo de los demás tests, y se carga con {@code @Import} en {@code ManejadorGlobalDeErroresTest}.
 */
@RestController
public class ControladorDeErroresDePrueba {

    public record AltaDePrueba(@NotBlank String nombre, @NotBlank @Email String email) {
    }

    @GetMapping("/api/v1/pruebas/conflicto")
    public void conflicto() {
        throw new ConflictoDeNegocio("SLUG_EN_USO", "El slug ya está en uso");
    }

    @GetMapping("/api/v1/pruebas/con-propiedades")
    public void conPropiedades() {
        throw new ValidacionDeNegocio("FRANJAS_SOLAPADAS", "Hay franjas solapadas", Map.of("dias", List.of(1, 3)));
    }

    @GetMapping("/api/v1/pruebas/franjas-solapadas-en-la-base")
    public void franjasSolapadasEnLaBase() {
        throw new org.springframework.dao.DataIntegrityViolationException(
                "conflicting key value violates exclusion constraint \"ex_disponibilidad_sin_solape\"");
    }

    @GetMapping("/api/v1/pruebas/turno-solapado-en-la-base")
    public void turnoSolapadoEnLaBase() {
        throw new org.springframework.dao.DataIntegrityViolationException(
                "conflicting key value violates exclusion constraint \"ex_turno_sin_solapamiento\"");
    }

    @GetMapping("/api/v1/pruebas/prestacion-duplicada-en-la-base")
    public void prestacionDuplicadaEnLaBase() {
        throw new org.springframework.dao.DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"uk_tipo_prestacion_nombre\"");
    }

    @GetMapping("/api/v1/pruebas/no-encontrado")
    public void noEncontrado() {
        throw new RecursoNoEncontrado("recurso de prueba");
    }

    @GetMapping("/api/v1/pruebas/concurrencia")
    public void concurrencia() {
        throw new OptimisticLockingFailureException("versión desactualizada");
    }

    @GetMapping("/api/v1/pruebas/inesperado")
    public void inesperado() {
        throw new IllegalStateException("falló al procesar al paciente con teléfono +5491100000099 y mail paciente@example.com");
    }

    @GetMapping("/api/v1/pruebas/sin-tenant")
    public void sinTenant() {
        throw new TenantNoResueltoException();
    }

    @PostMapping("/api/v1/pruebas/alta")
    public void alta(@Valid @RequestBody AltaDePrueba alta) {
        // Solo valida el body.
    }
}
