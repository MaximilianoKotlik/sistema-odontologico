package ar.sistemaodontologico.auth.application;

import ar.sistemaodontologico.auth.domain.CredencialesDeUsuario;
import ar.sistemaodontologico.auth.domain.Usuario;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import java.util.UUID;
import ar.sistemaodontologico.auth.domain.NuevoUsuario;
import java.util.Optional;

/**
 * Puerto de persistencia del odontólogo. {@link #buscarPorEmail} es la única consulta sin consultorio: el login
 * todavía no tiene tenant y el email es único en todo el sistema (RN-TE-02, design D9).
 */
public interface UsuarioRepositorio {

    void guardarNuevo(NuevoUsuario usuario);

    Optional<CredencialesDeUsuario> buscarPorEmail(String email);

    /**
     * Como {@link #buscarPorEmail}, pero bloquea la fila ({@code FOR UPDATE}): dos logins fallidos simultáneos no
     * pueden contar como uno solo (C-05, design D1).
     */
    Optional<Usuario> buscarPorEmailParaActualizar(String email);

    /** Bloquea la fila del usuario de ese consultorio ({@code FOR UPDATE}). Vacío si no pertenece a él. */
    Optional<Usuario> buscarParaActualizar(ConsultorioId consultorio, UUID id);

    /** Guarda el contador de fallos y el bloqueo (design D1). */
    void guardarAcceso(Usuario usuario);

    /** Guarda la contraseña nueva, la versión de credenciales y el desbloqueo (design D6). */
    void guardarContrasena(Usuario usuario);
}
