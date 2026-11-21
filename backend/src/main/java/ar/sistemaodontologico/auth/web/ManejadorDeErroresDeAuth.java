package ar.sistemaodontologico.auth.web;

import ar.sistemaodontologico.auth.application.LimiteExcedido;
import ar.sistemaodontologico.compartido.web.ManejadorGlobalDeErrores;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Errores de auth que no son {@code ErrorDeNegocio} porque llevan datos propios (C-05). Va antes que el manejador
 * global, que respondería 500.
 * <ul>
 *   <li>Límites por cuenta: 429 {@code DEMASIADAS_SOLICITUDES} con {@code Retry-After}, igual que el filtro por IP
 *       (design D8).</li>
 *   <li>Cuenta bloqueada: 423 {@code CUENTA_BLOQUEADA} con un mensaje genérico y los minutos que faltan (RN-AU-03,
 *       FL-01; design D1).</li>
 * </ul>
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class ManejadorDeErroresDeAuth {

    @ExceptionHandler(CuentaBloqueada.class)
    ResponseEntity<ProblemDetail> cuentaBloqueada(CuentaBloqueada error, HttpServletRequest peticion) {
        ProblemDetail problema = ManejadorGlobalDeErrores.problemaPara(HttpStatus.LOCKED, "CUENTA_BLOQUEADA",
                "La cuenta está bloqueada temporalmente por intentos fallidos", peticion.getRequestURI());
        problema.setProperty("minutosRestantes", error.minutosRestantes());
        return ResponseEntity.status(HttpStatus.LOCKED).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problema);
    }

    @ExceptionHandler(LimiteExcedido.class)
    ResponseEntity<ProblemDetail> limiteExcedido(LimiteExcedido error, HttpServletRequest peticion) {
        ProblemDetail problema = ManejadorGlobalDeErrores.problemaPara(HttpStatus.TOO_MANY_REQUESTS,
                "DEMASIADAS_SOLICITUDES", "Demasiadas solicitudes. Probá de nuevo en unos minutos",
                peticion.getRequestURI());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(error.segundosParaReintentar()))
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problema);
    }
}
