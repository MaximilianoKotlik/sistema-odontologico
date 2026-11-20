package ar.sistemaodontologico.auth.application;

import ar.sistemaodontologico.auth.application.EmisorDeAccessToken.AccessToken;
import ar.sistemaodontologico.auth.domain.CredencialesDeUsuario;
import ar.sistemaodontologico.auth.domain.HashDeToken;
import ar.sistemaodontologico.auth.domain.RefreshToken;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import ar.sistemaodontologico.compartido.domain.GeneradorDeIds;
import ar.sistemaodontologico.compartido.domain.error.AutenticacionRechazada;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login, rotación del refresh y logout (spec sesion-odontologo, RN-AU-02, design D4 y D5).
 *
 * <p>La renovación devuelve un resultado en lugar de lanzar una excepción cuando la rechaza: así la transacción
 * confirma la revocación de toda la familia ante un reuso (si lanzara, el rollback la desharía).
 */
@Service
public class GestionDeSesion {

    /** Sesión emitida: el access token va en el cuerpo y el refresh en la cookie. */
    public record Sesion(AccessToken access, String refresh, Duration vigenciaRefresh) {

        @Override
        public String toString() {
            return "Sesion[" + access + "]";
        }
    }

    public sealed interface Renovacion {
    }

    public record Renovada(Sesion sesion) implements Renovacion {
    }

    public record Rechazada() implements Renovacion {
    }


    private final UsuarioRepositorio usuarios;
    private final RefreshTokenRepositorio refreshTokens;
    private final HasheadorDeContrasena hasheador;
    private final EmisorDeAccessToken emisor;
    private final GeneradorDeSecretos secretos;
    private final VersionDeCredenciales versiones;
    private final GeneradorDeIds ids;
    private final Clock reloj;
    private final VigenciasDeSesion vigencias;
    /** Hash real (costo 12) de un valor descartable: se compara contra él cuando el email no existe. */
    private final String hashFicticio;

    public GestionDeSesion(UsuarioRepositorio usuarios, RefreshTokenRepositorio refreshTokens,
            HasheadorDeContrasena hasheador, EmisorDeAccessToken emisor, GeneradorDeSecretos secretos,
            VersionDeCredenciales versiones, GeneradorDeIds ids, Clock reloj, VigenciasDeSesion vigencias) {
        this.usuarios = usuarios;
        this.refreshTokens = refreshTokens;
        this.hasheador = hasheador;
        this.emisor = emisor;
        this.secretos = secretos;
        this.versiones = versiones;
        this.ids = ids;
        this.reloj = reloj;
        this.vigencias = vigencias;
        this.hashFicticio = hasheador.hashear(UUID.randomUUID().toString());
    }

    @Transactional
    public Sesion iniciar(String email, String contrasena) {
        Optional<CredencialesDeUsuario> credenciales = email == null ? Optional.empty() : usuarios.buscarPorEmail(email);
        // Siempre se compara un hash, exista o no el email: el tiempo de respuesta no revela si la cuenta existe.
        String hash = credenciales.map(CredencialesDeUsuario::passwordHash).orElse(hashFicticio);
        boolean coincide = hasheador.coincide(contrasena == null ? "" : contrasena, hash);
        if (credenciales.isEmpty() || !coincide) {
            throw new AutenticacionRechazada("CREDENCIALES_INVALIDAS", "Email o contraseña incorrectos");
        }
        CredencialesDeUsuario usuario = credenciales.get();
        return emitirSesion(usuario.id(), usuario.consultorio(), usuario.credencialesVersion());
    }

    @Transactional
    public Renovacion renovar(String refresh) {
        if (refresh == null || refresh.isBlank()) {
            return new Rechazada();
        }
        Instant ahora = Instant.now(reloj);
        Optional<RefreshToken> guardado = refreshTokens.buscarPorHashParaActualizar(HashDeToken.sha256(refresh));
        if (guardado.isEmpty()) {
            return new Rechazada();
        }
        RefreshToken token = guardado.get();
        if (token.revocado()) {
            // Reuso de un refresh ya rotado o revocado: se cierran todas las sesiones del usuario (RN-AU-02).
            refreshTokens.revocarTodos(token.consultorio(), token.usuario(), ahora);
            return new Rechazada();
        }
        if (token.vencido(ahora)) {
            return new Rechazada();
        }
        Optional<Integer> version = versiones.vigente(token.consultorio(), token.usuario());
        if (version.isEmpty()) {
            return new Rechazada();
        }
        refreshTokens.revocar(token.consultorio(), token.id(), ahora);
        return new Renovada(emitirSesion(token.usuario(), token.consultorio(), version.get()));
    }

    @Transactional
    public void cerrar(String refresh) {
        if (refresh == null || refresh.isBlank()) {
            return;
        }
        refreshTokens.buscarPorHashParaActualizar(HashDeToken.sha256(refresh))
                .filter(token -> !token.revocado())
                .ifPresent(token -> refreshTokens.revocar(token.consultorio(), token.id(), Instant.now(reloj)));
    }

    private Sesion emitirSesion(UUID usuario, ConsultorioId consultorio, int version) {
        String refresh = secretos.nuevo();
        refreshTokens.guardar(ids.nuevo(), consultorio, usuario, HashDeToken.sha256(refresh),
                Instant.now(reloj).plus(vigencias.refresh()));
        return new Sesion(emisor.emitir(usuario, consultorio, version), refresh, vigencias.refresh());
    }
}
