package ar.sistemaodontologico.auth.infrastructure.seguridad;

import ar.sistemaodontologico.compartido.web.ManejadorGlobalDeErrores;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Los rechazos de la cadena de seguridad, en el mismo Problem Details que el resto de la API (design D8). El 401
 * es siempre igual: no explica si el token venció, está mal firmado o es de credenciales viejas.
 */
@Component
class RespuestasDeSeguridad {

    static final String NO_AUTENTICADO = "NO_AUTENTICADO";

    private final ObjectMapper json;

    RespuestasDeSeguridad(ObjectMapper json) {
        this.json = json;
    }

    AuthenticationEntryPoint noAutenticado() {
        return (peticion, respuesta, error) -> {
            respuesta.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            escribir(peticion, respuesta, HttpStatus.UNAUTHORIZED, NO_AUTENTICADO, "Hace falta iniciar sesión");
        };
    }

    AccessDeniedHandler accesoDenegado() {
        return (peticion, respuesta, error) ->
                escribir(peticion, respuesta, HttpStatus.FORBIDDEN, "ACCESO_DENEGADO", "Acceso denegado");
    }

    void escribir(HttpServletRequest peticion, HttpServletResponse respuesta, HttpStatus estado, String codigo,
            String detalle) throws IOException {
        ProblemDetail problema = ManejadorGlobalDeErrores.problemaPara(estado, codigo, detalle, peticion.getRequestURI());
        respuesta.setStatus(estado.value());
        respuesta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        json.writeValue(respuesta.getOutputStream(), problema);
    }
}
