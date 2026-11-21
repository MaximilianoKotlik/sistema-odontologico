package ar.sistemaodontologico.exportacion.web;

import ar.sistemaodontologico.compartido.application.SesionActual;
import ar.sistemaodontologico.compartido.application.TenantContext;
import ar.sistemaodontologico.compartido.web.ManejadorGlobalDeErrores;
import ar.sistemaodontologico.exportacion.application.ExportarDatos;
import ar.sistemaodontologico.exportacion.application.PedidoDeExportacion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * {@code POST /api/v1/exportacion} (C-19, US-050, design D1). El pedido y el 429 se resuelven acá, en el hilo de la
 * petición: el cuerpo corre después en otro hilo, sin tenant ni sesión (design D2).
 */
@RestController
@RequestMapping("/api/v1/exportacion")
class ControladorDeExportacion {

    private static final MediaType ZIP = MediaType.parseMediaType("application/zip");

    private final ExportarDatos exportar;
    private final TenantContext tenant;
    private final SesionActual sesion;
    private final Clock clock;

    ControladorDeExportacion(ExportarDatos exportar, TenantContext tenant, SesionActual sesion, Clock clock) {
        this.exportar = exportar;
        this.tenant = tenant;
        this.sesion = sesion;
        this.clock = clock;
    }

    @PostMapping
    @Operation(summary = "Descarga todos los datos del consultorio en un ZIP")
    @ApiResponse(responseCode = "200", description = "ZIP con JSON, CSV, PDF y manifest",
            content = @Content(mediaType = "application/zip", schema = @Schema(type = "string", format = "binary")))
    @ApiResponse(responseCode = "401", description = "Sin sesión",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "429", description = "Exportación reciente o en curso; ver Retry-After",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<StreamingResponseBody> exportar() {
        PedidoDeExportacion pedido = new PedidoDeExportacion(tenant.actual(), sesion.usuarioId(),
                sesion.ip().orElse(null), clock.instant());
        ExportarDatos.Reserva reserva = exportar.reservar(pedido);
        if (!reserva.permitida()) {
            throw new ExportacionRechazada(reserva.reintentarEnSegundos());
        }
        try {
            StreamingResponseBody cuerpo = salida -> exportar.generar(pedido, salida);
            return ResponseEntity.ok()
                    .contentType(ZIP)
                    .cacheControl(CacheControl.noStore())
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.attachment().filename(reserva.nombreArchivo()).build().toString())
                    .body(cuerpo);
        } catch (RuntimeException e) {
            exportar.liberar(pedido);
            throw e;
        }
    }

    /**
     * El 429 sale por excepción porque Spring activa el streaming solo si el método declara
     * {@code ResponseEntity<StreamingResponseBody>}, y ese tipo no admite un Problem Details como cuerpo.
     */
    @ExceptionHandler(ExportacionRechazada.class)
    ResponseEntity<ProblemDetail> rechazo(ExportacionRechazada rechazo, HttpServletRequest peticion) {
        ProblemDetail problema = ManejadorGlobalDeErrores.problemaPara(HttpStatus.TOO_MANY_REQUESTS,
                "EXPORTACION_RECIENTE", "Ya hay una exportación reciente o en curso", peticion.getRequestURI());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(rechazo.segundos))
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problema);
    }

    private static final class ExportacionRechazada extends RuntimeException {

        private final long segundos;

        private ExportacionRechazada(long segundos) {
            super("Exportación rechazada por el límite", null, false, false);
            this.segundos = segundos;
        }
    }
}
