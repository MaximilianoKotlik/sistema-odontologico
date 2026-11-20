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
 * Traduce los límites por cuenta de los casos de uso a 429 {@code DEMASIADAS_SOLICITUDES} con {@code Retry-After},
 * igual que el filtro por IP (C-05, design D8). Va antes que el manejador global, que respondería 500.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class ManejadorDeLimites {

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
