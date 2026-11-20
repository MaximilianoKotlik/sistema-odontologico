package ar.sistemaodontologico.auth.application;

import ar.sistemaodontologico.auth.domain.HashDeToken;
import ar.sistemaodontologico.auth.domain.PoliticaDeContrasena;
import ar.sistemaodontologico.auth.domain.TokenDeRecuperacion;
import ar.sistemaodontologico.auth.domain.Usuario;
import ar.sistemaodontologico.compartido.application.PublicadorDeAuditoria;
import ar.sistemaodontologico.compartido.domain.auditoria.Actor;
import ar.sistemaodontologico.compartido.domain.auditoria.ActorTipo;
import ar.sistemaodontologico.compartido.domain.auditoria.CambioAuditado;
import ar.sistemaodontologico.compartido.domain.error.ValidacionDeNegocio;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Restablecimiento de la contraseña con el token del email (US-003, RN-AU-04 y RN-AU-05; C-05, design D3 y D6).
 *
 * <p>Primero se valida el token y recién después la política de la contraseña nueva: quien no tiene un token
 * vigente no puede averiguar si una contraseña la cumple, y una contraseña débil no consume el token.
 */
@Service
public class ConfirmarRecuperacion {

    private final TokenDeRecuperacionRepositorio tokens;
    private final UsuarioRepositorio usuarios;
    private final NuevaContrasena nuevaContrasena;
    private final PublicadorDeAuditoria auditoria;
    private final Clock reloj;

    public ConfirmarRecuperacion(TokenDeRecuperacionRepositorio tokens, UsuarioRepositorio usuarios,
            NuevaContrasena nuevaContrasena, PublicadorDeAuditoria auditoria, Clock reloj) {
        this.tokens = tokens;
        this.usuarios = usuarios;
        this.nuevaContrasena = nuevaContrasena;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    @Transactional
    public void confirmar(String token, String contrasena, String ip) {
        Instant ahora = Instant.now(reloj);
        if (token == null || token.isBlank()) {
            throw tokenInvalido();
        }
        TokenDeRecuperacion vigente = tokens.buscarPorHashParaActualizar(HashDeToken.sha256(token))
                .filter(guardado -> guardado.vigente(ahora))
                .orElseThrow(ConfirmarRecuperacion::tokenInvalido);
        Usuario usuario = usuarios.buscarParaActualizar(vigente.consultorio(), vigente.usuario())
                .orElseThrow(ConfirmarRecuperacion::tokenInvalido);
        PoliticaDeContrasena.validar(contrasena, usuario.email());
        Usuario actualizado = nuevaContrasena.establecer(usuario, contrasena, ahora);
        tokens.marcarUsado(vigente.consultorio(), vigente.id(), ahora);
        auditoria.publicar(new CambioAuditado(actualizado.consultorio(),
                new Actor(ActorTipo.ODONTOLOGO, actualizado.id()), "usuario", actualizado.id(), "PASSWORD_RECUPERADA",
                null, Map.of("credenciales_version", actualizado.credencialesVersion()), ip));
    }

    /** Mismo código y mensaje para un token vencido, usado, revocado o desconocido (spec: Token inválido). */
    private static ValidacionDeNegocio tokenInvalido() {
        return new ValidacionDeNegocio("TOKEN_INVALIDO", "El link de recuperación no es válido o venció");
    }
}
