package pruebas.errores;

import ar.sistemaodontologico.compartido.application.TenantNoResueltoException;
import ar.sistemaodontologico.compartido.domain.error.ConflictoDeNegocio;
import ar.sistemaodontologico.compartido.domain.error.RecursoNoEncontrado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

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
