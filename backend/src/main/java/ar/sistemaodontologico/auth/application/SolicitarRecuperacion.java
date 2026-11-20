package ar.sistemaodontologico.auth.application;

import ar.sistemaodontologico.auth.domain.HashDeToken;
import ar.sistemaodontologico.auth.domain.TokenDeRecuperacion;
import ar.sistemaodontologico.auth.domain.Usuario;
import ar.sistemaodontologico.compartido.domain.GeneradorDeIds;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Solicitud de recuperación de contraseña (US-003, RN-AU-04; C-05, design D3 y D4). Responde igual exista o no la
 * cuenta: si existe, emite un token nuevo (revocando los anteriores) y el email sale después del commit, así la
 * respuesta no espera al servidor de correo.
 */
@Service
public class SolicitarRecuperacion {

    private final UsuarioRepositorio usuarios;
    private final TokenDeRecuperacionRepositorio tokens;
    private final GeneradorDeSecretos secretos;
    private final GeneradorDeIds ids;
    private final Clock reloj;
    private final ApplicationEventPublisher eventos;

    public SolicitarRecuperacion(UsuarioRepositorio usuarios, TokenDeRecuperacionRepositorio tokens,
            GeneradorDeSecretos secretos, GeneradorDeIds ids, Clock reloj, ApplicationEventPublisher eventos) {
        this.usuarios = usuarios;
        this.tokens = tokens;
        this.secretos = secretos;
        this.ids = ids;
        this.reloj = reloj;
        this.eventos = eventos;
    }

    @Transactional
    public void solicitar(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        Optional<Usuario> cuenta = usuarios.buscarPorEmailParaActualizar(email);
        if (cuenta.isEmpty()) {
            return;
        }
        Usuario usuario = cuenta.get();
        Instant ahora = Instant.now(reloj);
        tokens.revocarVigentes(usuario.consultorio(), usuario.id(), ahora);
        String token = secretos.nuevo();
        tokens.guardar(TokenDeRecuperacion.emitir(ids.nuevo(), usuario.consultorio(), usuario.id(), ahora),
                HashDeToken.sha256(token));
        eventos.publishEvent(new RecuperacionSolicitada(usuario.id(), usuario.email(), token));
    }
}
