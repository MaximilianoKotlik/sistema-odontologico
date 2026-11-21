package ar.sistemaodontologico.compartido.web;

import ar.sistemaodontologico.compartido.domain.EnmascaradorDePii;
import ar.sistemaodontologico.compartido.domain.error.ErrorDeNegocio;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Traduce toda excepción a Problem Details (RFC 7807) con un {@code codigo} estable (spec errores-api,
 * RN-GL-03, design D4). El mapeo de errores de negocio a HTTP vive solo acá.
 */
@RestControllerAdvice
public class ManejadorGlobalDeErrores extends ResponseEntityExceptionHandler {

    static final String PROPIEDAD_CODIGO = "codigo";
    static final String PROPIEDAD_ERRORES = "errores";
    static final String PROPIEDAD_REFERENCIA = "referencia";

    private static final Logger LOG = LoggerFactory.getLogger(ManejadorGlobalDeErrores.class);
    private static final String PREFIJO_TYPE = "urn:agenda:error:";

    /** Constraints de la base que representan un error de negocio esperado (design D6). */
    private static final Map<String, ErrorDeConstraint> ERRORES_DE_CONSTRAINT = Map.of(
            "uk_consultorio_slug", new ErrorDeConstraint(HttpStatus.CONFLICT, "SLUG_EN_USO"),
            // C-04: dos registros simultáneos con el mismo email (el chequeo previo no ve la carrera).
            "uk_usuario_email", new ErrorDeConstraint(HttpStatus.CONFLICT, "EMAIL_EN_USO"),
            // C-08 (design D5 y D8): carreras que el chequeo previo de la aplicación no ve.
            "uk_tipo_prestacion_nombre", new ErrorDeConstraint(HttpStatus.CONFLICT, "PRESTACION_DUPLICADA"),
            "ex_disponibilidad_sin_solape", new ErrorDeConstraint(HttpStatus.BAD_REQUEST, "FRANJAS_SOLAPADAS"),
            // C-11 (design D5): red de seguridad si una carrera saltea la validación; no dice con qué turno chocó.
            "ex_turno_sin_solapamiento", new ErrorDeConstraint(HttpStatus.CONFLICT, "TURNO_SOLAPADO"));

    private static final Map<Integer, String> CODIGOS_POR_ESTADO = Map.of(
            400, "SOLICITUD_INVALIDA",
            404, "RECURSO_NO_ENCONTRADO",
            405, "METODO_NO_PERMITIDO",
            406, "FORMATO_NO_ACEPTABLE",
            409, "CONFLICTO",
            413, "CUERPO_DEMASIADO_GRANDE",
            415, "TIPO_DE_CONTENIDO_NO_SOPORTADO",
            500, "ERROR_INTERNO",
            503, "SERVICIO_NO_DISPONIBLE");

    private record ErrorDeConstraint(HttpStatus estado, String codigo) {
    }

    public record ErrorDeCampo(String campo, String codigo) {
    }

    @ExceptionHandler(ErrorDeNegocio.class)
    ResponseEntity<Object> errorDeNegocio(ErrorDeNegocio error, WebRequest peticion) {
        HttpStatus estado = switch (error.getTipo()) {
            case VALIDACION -> HttpStatus.BAD_REQUEST;
            case NO_AUTENTICADO -> HttpStatus.UNAUTHORIZED;
            case NO_ENCONTRADO -> HttpStatus.NOT_FOUND;
            case CONFLICTO -> HttpStatus.CONFLICT;
            case NO_DISPONIBLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        ProblemDetail problema = problema(estado, error.getCodigo(), error.getMessage(), peticion);
        error.getPropiedades().forEach(problema::setProperty);
        return respuesta(problema);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<Object> concurrencia(OptimisticLockingFailureException error, WebRequest peticion) {
        return respuesta(problema(HttpStatus.CONFLICT, "CONFLICTO_CONCURRENCIA",
                "El recurso cambió mientras se editaba", peticion));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Object> integridad(DataIntegrityViolationException error, WebRequest peticion) {
        Optional<ErrorDeConstraint> conocido = constraintConocida(error);
        if (conocido.isPresent()) {
            ErrorDeConstraint constraint = conocido.get();
            return respuesta(problema(constraint.estado(), constraint.codigo(), "Conflicto con un dato existente", peticion));
        }
        return inesperado(error, peticion);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> inesperado(Exception error, WebRequest peticion) {
        String referencia = UUID.randomUUID().toString();
        LOG.error("Error inesperado [referencia {}]", referencia, enmascarada(error));
        ProblemDetail problema = problema(HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO",
                "Ocurrió un error inesperado", peticion);
        problema.setProperty(PROPIEDAD_REFERENCIA, referencia);
        return respuesta(problema);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException error,
            HttpHeaders cabeceras, HttpStatusCode estado, WebRequest peticion) {
        List<ErrorDeCampo> errores = error.getBindingResult().getFieldErrors().stream()
                .map(campo -> new ErrorDeCampo(campo.getField(), codigoDeRestriccion(campo.getCode())))
                .toList();
        ProblemDetail problema = problema(HttpStatus.BAD_REQUEST, "VALIDACION", "Hay datos inválidos", peticion);
        problema.setProperty(PROPIEDAD_ERRORES, errores);
        return respuesta(problema);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException error,
            HttpHeaders cabeceras, HttpStatusCode estado, WebRequest peticion) {
        return respuesta(problema(HttpStatus.BAD_REQUEST, "CUERPO_INVALIDO", "El cuerpo de la petición no es válido", peticion));
    }

    /** El resto de las excepciones de Spring MVC (404 de ruta, 405, 415…) también llevan {@code codigo}. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception error, @Nullable Object cuerpo,
            HttpHeaders cabeceras, HttpStatusCode estado, WebRequest peticion) {
        ProblemDetail problema = cuerpo instanceof ProblemDetail existente
                ? existente
                : ProblemDetail.forStatus(estado);
        String codigo = CODIGOS_POR_ESTADO.getOrDefault(estado.value(), "ERROR_HTTP_" + estado.value());
        completar(problema, codigo, peticion);
        return ResponseEntity.status(estado).headers(cabeceras).body(problema);
    }

    private static ProblemDetail problema(HttpStatus estado, String codigo, String detalle, WebRequest peticion) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setTitle(estado.getReasonPhrase());
        completar(problema, codigo, peticion);
        return problema;
    }

    private static void completar(ProblemDetail problema, String codigo, WebRequest peticion) {
        problema.setType(URI.create(PREFIJO_TYPE + codigo.toLowerCase(Locale.ROOT).replace('_', '-')));
        problema.setProperty(PROPIEDAD_CODIGO, codigo);
        if (peticion instanceof ServletWebRequest servlet) {
            problema.setInstance(URI.create(servlet.getRequest().getRequestURI()));
        }
    }

    /**
     * Problem Details con el mismo formato que el resto de la API, para quien responde fuera de Spring MVC
     * (por ejemplo los filtros de seguridad de C-04).
     */
    public static ProblemDetail problemaPara(HttpStatus estado, String codigo, String detalle, String rutaPedida) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setTitle(estado.getReasonPhrase());
        problema.setType(URI.create(PREFIJO_TYPE + codigo.toLowerCase(Locale.ROOT).replace('_', '-')));
        problema.setProperty(PROPIEDAD_CODIGO, codigo);
        problema.setInstance(URI.create(rutaPedida));
        return problema;
    }

    private static ResponseEntity<Object> respuesta(ProblemDetail problema) {
        return ResponseEntity.status(problema.getStatus()).body(problema);
    }

    private static Optional<ErrorDeConstraint> constraintConocida(DataIntegrityViolationException error) {
        String mensaje = String.valueOf(error.getMostSpecificCause().getMessage());
        return ERRORES_DE_CONSTRAINT.entrySet().stream()
                .filter(entrada -> mensaje.contains(entrada.getKey()))
                .map(Entry::getValue)
                .findFirst();
    }

    /** {@code NotBlank} → {@code NOT_BLANK}. */
    static String codigoDeRestriccion(@Nullable String restriccion) {
        if (restriccion == null) {
            return "INVALIDO";
        }
        return restriccion.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT);
    }

    /** Copia la excepción con los mensajes enmascarados y la misma traza, para el log (RN-GL-01). */
    private static Throwable enmascarada(Throwable original) {
        Throwable causa = original.getCause() == null || original.getCause() == original
                ? null
                : enmascarada(original.getCause());
        Throwable copia = new ExcepcionEnmascarada(
                original.getClass().getName() + ": " + EnmascaradorDePii.enmascarar(original.getMessage()), causa);
        copia.setStackTrace(original.getStackTrace());
        return copia;
    }

    private static final class ExcepcionEnmascarada extends RuntimeException {

        ExcepcionEnmascarada(String mensaje, Throwable causa) {
            super(mensaje, causa, false, true);
        }

        @Override
        public String toString() {
            return getMessage();
        }
    }
}
