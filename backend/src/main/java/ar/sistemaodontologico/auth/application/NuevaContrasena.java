package ar.sistemaodontologico.auth.application;

import ar.sistemaodontologico.auth.domain.Usuario;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * La única operación de "nueva contraseña" (C-05, design D6), que usan el restablecimiento y el cambio con sesión:
 * guarda el hash, sube la versión de credenciales (invalida los access tokens emitidos), desbloquea la cuenta y
 * revoca todos los refresh del usuario (RN-AU-05). Corre dentro de la transacción de quien la llama.
 */
@Component
public class NuevaContrasena {

    private final UsuarioRepositorio usuarios;
    private final RefreshTokenRepositorio refreshTokens;
    private final HasheadorDeContrasena hasheador;

    public NuevaContrasena(UsuarioRepositorio usuarios, RefreshTokenRepositorio refreshTokens,
            HasheadorDeContrasena hasheador) {
        this.usuarios = usuarios;
        this.refreshTokens = refreshTokens;
        this.hasheador = hasheador;
    }

    public Usuario establecer(Usuario usuario, String contrasena, Instant ahora) {
        Usuario actualizado = usuario.establecerContrasena(hasheador.hashear(contrasena), ahora);
        usuarios.guardarContrasena(actualizado);
        refreshTokens.revocarTodos(actualizado.consultorio(), actualizado.id(), ahora);
        return actualizado;
    }
}
