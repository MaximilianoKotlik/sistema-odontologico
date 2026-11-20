package pruebas.aislamiento;

import ar.sistemaodontologico.compartido.application.TenantContext;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Solo de test: simula la capa de seguridad que en C-04 establecerá el tenant desde el claim {@code cid}
 * del JWT. Acá lo toma de una cabecera para poder probar el aislamiento antes de que exista la autenticación.
 */
public class FiltroDeTenantDePrueba extends OncePerRequestFilter {

    public static final String CABECERA = "X-Consultorio-Sesion-Prueba";

    private final TenantContext tenant;

    public FiltroDeTenantDePrueba(TenantContext tenant) {
        this.tenant = tenant;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        String consultorio = peticion.getHeader(CABECERA);
        if (consultorio != null) {
            tenant.establecer(new ConsultorioId(UUID.fromString(consultorio)));
        }
        cadena.doFilter(peticion, respuesta);
    }
}
