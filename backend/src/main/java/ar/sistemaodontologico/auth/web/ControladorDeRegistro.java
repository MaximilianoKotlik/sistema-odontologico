package ar.sistemaodontologico.auth.web;

import ar.sistemaodontologico.auth.application.RegistrarOdontologo;
import ar.sistemaodontologico.auth.application.RegistrarOdontologo.Resultado;
import ar.sistemaodontologico.auth.application.RegistrarOdontologo.Solicitud;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/v1/auth/registro} (US-001). Responde 201 sin abrir sesión: el frontend hace login a
 * continuación (design D1). La contraseña la valida la política de dominio, no Bean Validation, para que una
 * contraseña corta responda {@code PASSWORD_DEBIL} y no {@code VALIDACION}.
 */
@RestController
@RequestMapping("/api/v1/auth")
class ControladorDeRegistro {

    record RegistroRequest(
            @NotBlank @Size(max = 80) String nombre,
            @NotBlank @Size(max = 80) String apellido,
            @NotBlank @Email @Size(max = 254) String email,
            String password,
            @NotBlank @Size(max = 120) String nombreConsultorio,
            Boolean aceptaTerminos) {

        @Override
        public String toString() {
            return "RegistroRequest[consultorio=" + nombreConsultorio + "]";
        }
    }

    record RegistroResponse(UUID usuarioId, UUID consultorioId, String slug) {
    }

    private final RegistrarOdontologo registro;

    ControladorDeRegistro(RegistrarOdontologo registro) {
        this.registro = registro;
    }

    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.CREATED)
    RegistroResponse registrar(@Valid @RequestBody RegistroRequest pedido) {
        Resultado resultado = registro.registrar(new Solicitud(pedido.nombre(), pedido.apellido(), pedido.email(),
                pedido.password(), pedido.nombreConsultorio(), pedido.aceptaTerminos()));
        return new RegistroResponse(resultado.usuarioId(), resultado.consultorioId().valor(), resultado.slug());
    }
}
