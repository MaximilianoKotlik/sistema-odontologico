package ar.sistemaodontologico.auth.web;

import ar.sistemaodontologico.auth.application.GestionDeSesion;
import ar.sistemaodontologico.auth.application.GestionDeSesion.Bloqueada;
import ar.sistemaodontologico.auth.application.GestionDeSesion.CredencialesInvalidas;
import ar.sistemaodontologico.auth.application.GestionDeSesion.Exitoso;
import ar.sistemaodontologico.auth.application.GestionDeSesion.Renovada;
import ar.sistemaodontologico.auth.application.GestionDeSesion.Renovacion;
import ar.sistemaodontologico.auth.application.GestionDeSesion.ResultadoLogin;
import ar.sistemaodontologico.auth.application.GestionDeSesion.Sesion;
import ar.sistemaodontologico.compartido.domain.error.AutenticacionRechazada;
import ar.sistemaodontologico.compartido.web.ManejadorGlobalDeErrores;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login, refresh y logout (spec sesion-odontologo). El access token va en el cuerpo, para que el frontend lo
 * guarde en memoria; el refresh va solo en una cookie {@code HttpOnly; Secure; SameSite=Strict} limitada a
 * {@code /api/v1/auth}.
 */
@RestController
@RequestMapping("/api/v1/auth")
class ControladorDeSesion {

    static final String COOKIE_REFRESH = "refresh";
    static final String RUTA_COOKIE = "/api/v1/auth";

    record LoginRequest(String email, String password) {

        @Override
        public String toString() {
            return "LoginRequest[]";
        }
    }

    record SesionResponse(String accessToken, long expiraEn) {
    }

    private final GestionDeSesion sesiones;

    ControladorDeSesion(GestionDeSesion sesiones) {
        this.sesiones = sesiones;
    }

    @PostMapping("/login")
    ResponseEntity<SesionResponse> login(@RequestBody LoginRequest pedido, HttpServletRequest peticion) {
        ResultadoLogin resultado = sesiones.iniciar(pedido.email(), pedido.password(), peticion.getRemoteAddr());
        // Los rechazos se lanzan recién acá, con la transacción del login (contador y evento) ya confirmada.
        return switch (resultado) {
            case Exitoso exitoso -> conSesion(exitoso.sesion());
            case CredencialesInvalidas invalidas ->
                    throw new AutenticacionRechazada("CREDENCIALES_INVALIDAS", "Email o contraseña incorrectos");
            case Bloqueada bloqueada -> throw new CuentaBloqueada(bloqueada.minutosRestantes());
        };
    }

    @PostMapping("/refresh")
    ResponseEntity<?> refresh(@CookieValue(name = COOKIE_REFRESH, required = false) String refresh,
            HttpServletRequest peticion) {
        Renovacion renovacion = sesiones.renovar(refresh);
        if (renovacion instanceof Renovada renovada) {
            return conSesion(renovada.sesion());
        }
        // Rechazada: 401 SESION_EXPIRADA y la cookie se borra, para que el navegador no la vuelva a mandar.
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .header(HttpHeaders.SET_COOKIE, cookieBorrada().toString())
                .body(ManejadorGlobalDeErrores.problemaPara(HttpStatus.UNAUTHORIZED, "SESION_EXPIRADA",
                        "La sesión expiró", peticion.getRequestURI()));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@CookieValue(name = COOKIE_REFRESH, required = false) String refresh) {
        sesiones.cerrar(refresh);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookieBorrada().toString()).build();
    }

    private static ResponseEntity<SesionResponse> conSesion(Sesion sesion) {
        ResponseCookie cookie = cookie(sesion.refresh(), sesion.vigenciaRefresh());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new SesionResponse(sesion.access().valor(), sesion.access().vigencia().toSeconds()));
    }

    static ResponseCookie cookieBorrada() {
        return cookie("", Duration.ZERO);
    }

    private static ResponseCookie cookie(String valor, Duration vigencia) {
        return ResponseCookie.from(COOKIE_REFRESH, valor)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(RUTA_COOKIE)
                .maxAge(vigencia)
                .build();
    }
}
