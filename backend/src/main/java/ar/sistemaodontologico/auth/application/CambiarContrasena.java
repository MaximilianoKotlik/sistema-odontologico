package ar.sistemaodontologico.auth.application;

import ar.sistemaodontologico.auth.domain.PoliticaDeContrasena;
import ar.sistemaodontologico.auth.domain.Usuario;
import ar.sistemaodontologico.compartido.application.PublicadorDeAuditoria;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import ar.sistemaodontologico.compartido.domain.auditoria.Actor;
import ar.sistemaodontologico.compartido.domain.auditoria.ActorTipo;
import ar.sistemaodontologico.compartido.domain.auditoria.CambioAuditado;
import ar.sistemaodontologico.compartido.domain.error.AutenticacionRechazada;
import ar.sistemaodontologico.compartido.domain.error.ValidacionDeNegocio;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cambio de contraseña con sesión iniciada (US-005, RN-AU-05; C-05, design D6 y D7). Una contraseña actual
 * incorrecta no suma al contador de bloqueo del login: quien la escribe mal en un formulario no debería quedar
 * afuera de su cuenta.
 */
@Service
public class CambiarContrasena {

    private final UsuarioRepositorio usuarios;
    private final HasheadorDeContrasena hasheador;
    private final NuevaContrasena nuevaContrasena;
    private final PublicadorDeAuditoria auditoria;
    private final Clock reloj;
    private final LimitesPorCuenta limites;

    public CambiarContrasena(UsuarioRepositorio usuarios, HasheadorDeContrasena hasheador,
            NuevaContrasena nuevaContrasena, PublicadorDeAuditoria auditoria, Clock reloj, LimitesPorCuenta limites) {
        this.usuarios = usuarios;
        this.hasheador = hasheador;
        this.nuevaContrasena = nuevaContrasena;
        this.auditoria = auditoria;
        this.reloj = reloj;
        this.limites = limites;
    }

    @Transactional
    public void cambiar(ConsultorioId consultorio, UUID usuarioId, String actual, String nueva, String ip) {
        // Contra quien robó un access token y prueba contraseñas actuales (design D7 y D8).
        limites.consumirCambioDeContrasena(usuarioId);
        Usuario usuario = usuarios.buscarParaActualizar(consultorio, usuarioId)
                .orElseThrow(() -> new AutenticacionRechazada("NO_AUTENTICADO", "No autenticado"));
        if (actual == null || !hasheador.coincide(actual, usuario.passwordHash())) {
            throw new ValidacionDeNegocio("PASSWORD_ACTUAL_INVALIDA", "La contraseña actual no es correcta");
        }
        PoliticaDeContrasena.validar(nueva, usuario.email());
        Usuario actualizado = nuevaContrasena.establecer(usuario, nueva, Instant.now(reloj));
        auditoria.publicar(new CambioAuditado(consultorio, new Actor(ActorTipo.ODONTOLOGO, usuarioId), "usuario",
                usuarioId, "PASSWORD_CAMBIADA", null,
                Map.of("credenciales_version", actualizado.credencialesVersion()), ip));
    }
}
