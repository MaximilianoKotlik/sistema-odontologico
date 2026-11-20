package pruebas.seguridad;

import ar.sistemaodontologico.compartido.application.TenantContext;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Solo de test (C-04): una ruta privada que devuelve el consultorio activo que dejó la seguridad. */
@RestController
public class ControladorPrivadoDePrueba {

    private final TenantContext tenant;

    public ControladorPrivadoDePrueba(TenantContext tenant) {
        this.tenant = tenant;
    }

    @GetMapping("/api/v1/pruebas/privado")
    public Map<String, String> privado() {
        return Map.of("consultorio", tenant.actual().toString());
    }
}
