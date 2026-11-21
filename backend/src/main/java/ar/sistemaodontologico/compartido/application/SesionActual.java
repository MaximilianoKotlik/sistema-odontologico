package ar.sistemaodontologico.compartido.application;

import java.util.Optional;
import java.util.UUID;

/**
 * Odontólogo de la petición en curso, para el actor de la auditoría (C-08, design D3). Lo implementa la capa de
 * seguridad desde el access token ya validado; los casos de uso no dependen de Spring Security.
 */
public interface SesionActual {

    /** Id del usuario autenticado ({@code sub} del token); falla si la operación no tiene sesión. */
    UUID usuarioId();

    /** IP de origen de la petición, si la hay. */
    Optional<String> ip();
}
