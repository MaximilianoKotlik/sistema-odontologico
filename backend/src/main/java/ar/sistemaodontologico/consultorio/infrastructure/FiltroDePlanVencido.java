package ar.sistemaodontologico.consultorio.infrastructure;

import ar.sistemaodontologico.compartido.application.TenantContext;
import ar.sistemaodontologico.compartido.web.ManejadorGlobalDeErrores;
import ar.sistemaodontologico.consultorio.application.ConsultarPlan;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.RequestPath;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * Guarda de solo lectura con el plan vencido (C-20, RN-PL-03, RN-GL-04, design D4 con las pautas de Maxi en #53).
 * Rechaza con 403 {@code PLAN_VENCIDO} toda escritura bajo {@code /api/v1/} salvo una lista explícita; un endpoint
 * nuevo nace bloqueado. Corre después de la cadena de Spring Security: sin sesión ya respondió 401, y el tenant ya
 * está resuelto desde el token.
 *
 * <p>La ruta se compara con {@link PathPattern}, igual que Spring MVC al elegir el controlador: sin el context path,
 * con los segmentos decodificados y sin los parámetros {@code ;...}, y anclada al inicio y al final. Así no hay una
 * variante de la URL que la guarda vea como permitida y Spring resuelva a otra escritura.
 */
class FiltroDePlanVencido extends OncePerRequestFilter {

    static final String CODIGO = "PLAN_VENCIDO";
    static final String DETALLE = "El plan del consultorio está vencido: la agenda está en solo lectura";

    /** Solo estos métodos, escritos exactamente así, son lecturas; cualquier otro se trata como escritura. */
    private static final Set<String> LECTURAS = Set.of("GET", "HEAD", "OPTIONS");

    private static final PathPatternParser PARSER = new PathPatternParser();
    private static final PathPattern API = PARSER.parse("/api/v1/**");

    /** Método + ruta completa permitidos (spec solo-lectura-por-plan §Rutas permitidas). */
    private record Permitida(String metodo, PathPattern ruta) {

        boolean admite(String metodoPedido, PathContainer rutaPedida) {
            return (metodo == null || metodo.equals(metodoPedido)) && ruta.matches(rutaPedida);
        }
    }

    private static final List<Permitida> PERMITIDAS = List.of(
            // Prefijos a propósito: la API pública la gobierna la reserva (RN-RP-11); auth es sesión, registro,
            // recuperación y cambio de contraseña.
            new Permitida(null, PARSER.parse("/api/v1/public/**")),
            new Permitida(null, PARSER.parse("/api/v1/auth/**")),
            // Exportación (C-19, RN-EX-03).
            new Permitida("POST", PARSER.parse("/api/v1/exportacion")),
            // Marca de lectura de una novedad (DD-39): no cambia la agenda.
            new Permitida("POST", PARSER.parse("/api/v1/turnos/{id}/visto")));

    private final ConsultarPlan plan;
    private final TenantContext tenant;
    private final ObjectMapper json;

    FiltroDePlanVencido(ConsultarPlan plan, TenantContext tenant, ObjectMapper json) {
        this.plan = plan;
        this.tenant = tenant;
        this.json = json;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        if (bloquea(peticion)) {
            rechazar(peticion, respuesta);
            return;
        }
        cadena.doFilter(peticion, respuesta);
    }

    private boolean bloquea(HttpServletRequest peticion) {
        PathContainer ruta = RequestPath.parse(peticion.getRequestURI(), peticion.getContextPath())
                .pathWithinApplication();
        if (!esEscrituraBloqueable(peticion.getMethod(), ruta)) {
            return false;
        }
        // Sin tenant no hay sesión de odontólogo: Spring Security ya decidió (401) o la ruta no es privada.
        return tenant.estaResuelto() && plan.de(tenant.actual()).soloLectura();
    }

    /**
     * Si la guarda rechaza ese método y esa ruta con el plan vencido. La usa también el contrato OpenAPI, con la ruta
     * del mapeo ({@code {id}} es un segmento cualquiera).
     */
    static boolean esEscrituraBloqueable(String metodo, PathContainer ruta) {
        return !LECTURAS.contains(metodo) && API.matches(ruta)
                && PERMITIDAS.stream().noneMatch(permitida -> permitida.admite(metodo, ruta));
    }

    private void rechazar(HttpServletRequest peticion, HttpServletResponse respuesta) throws IOException {
        respuesta.setStatus(HttpStatus.FORBIDDEN.value());
        respuesta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        json.writeValue(respuesta.getOutputStream(), ManejadorGlobalDeErrores.problemaPara(HttpStatus.FORBIDDEN,
                CODIGO, DETALLE, peticion.getRequestURI()));
    }
}
