package ar.sistemaodontologico.auth.web;

import ar.sistemaodontologico.auth.application.ConfirmarRecuperacion;
import ar.sistemaodontologico.auth.application.SolicitarRecuperacion;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recuperación de contraseña (US-003, spec recuperacion-contrasena; C-05). Rutas públicas: quien la usa no tiene
 * sesión. El token llega en el cuerpo: el frontend lo lee del fragmento del link (design D5).
 */
@RestController
@RequestMapping("/api/v1/auth/recuperacion")
class ControladorDeRecuperacion {

    record SolicitudRequest(String email) {

        @Override
        public String toString() {
            return "SolicitudRequest[]";
        }
    }

    record ConfirmacionRequest(String token, String password) {

        @Override
        public String toString() {
            return "ConfirmacionRequest[]";
        }
    }

    private final SolicitarRecuperacion solicitud;
    private final ConfirmarRecuperacion confirmacion;

    ControladorDeRecuperacion(SolicitarRecuperacion solicitud, ConfirmarRecuperacion confirmacion) {
        this.solicitud = solicitud;
        this.confirmacion = confirmacion;
    }

    /** Siempre 202 y sin cuerpo, exista o no la cuenta (RN-AU-04). */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    void solicitar(@RequestBody SolicitudRequest pedido) {
        solicitud.solicitar(pedido.email());
    }

    @PostMapping("/confirmar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void confirmar(@RequestBody ConfirmacionRequest pedido, HttpServletRequest peticion) {
        confirmacion.confirmar(pedido.token(), pedido.password(), peticion.getRemoteAddr());
    }
}
