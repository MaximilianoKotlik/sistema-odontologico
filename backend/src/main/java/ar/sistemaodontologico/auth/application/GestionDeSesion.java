package ar.sistemaodontologico.auth.application;

import ar.sistemaodontologico.auth.application.EmisorDeAccessToken.AccessToken;
import ar.sistemaodontologico.auth.domain.HashDeToken;
import ar.sistemaodontologico.auth.domain.RefreshToken;
import ar.sistemaodontologico.auth.domain.Usuario;
import ar.sistemaodontologico.compartido.application.GeneradorDeSecretos;
import ar.sistemaodontologico.compartido.application.PublicadorDeAuditoria;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import ar.sistemaodontologico.compartido.domain.GeneradorDeIds;
import ar.sistemaodontologico.compartido.domain.auditoria.Actor;
import ar.sistemaodontologico.compartido.domain.auditoria.ActorTipo;
import ar.sistemaodontologico.compartido.domain.auditoria.CambioAuditado;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login, rotación del refresh y logout (spec sesion-odontologo, RN-AU-02, design D4 y D5 de C-04), con bloqueo
 * tras 5 fallos y auditoría de logins (C-05, design D1, D2 y D9).
 *
 * <p>El login y la renovación devuelven un resultado en lugar de lanzar una excepción cuando rechazan: así la
 * transacción confirma el contador de fallos, el evento {@code LOGIN_FALLIDO} o la revocación de toda la familia
 * ante un reuso (si lanzaran, el rollback los desharía).
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

    /** Resultado del login (C-05, design D2): {@code auth.web} lo traduce a 200, 401 o 423. */
    public sealed interface ResultadoLogin {
    }

    public record Exitoso(Sesion sesion) implements ResultadoLogin {
    }

    public record CredencialesInvalidas() implements ResultadoLogin {
    }

    public record Bloqueada(long minutosRestantes) implements ResultadoLogin {
    }

    static final String ENTIDAD = "usuario";


    private final UsuarioRepositorio usuarios;
    private final RefreshTokenRepositorio refreshTokens;
    private final HasheadorDeContrasena hasheador;
    private final EmisorDeAccessToken emisor;
    private final GeneradorDeSecretos secretos;
    private final VersionDeCredenciales versiones;
    private final GeneradorDeIds ids;
    private final Clock reloj;
    private final VigenciasDeSesion vigencias;
    private final PublicadorDeAuditoria auditoria;
    /** Hash real (costo 12) de un valor descartable: se compara contra él cuando el email no existe. */
    private final String hashFicticio;

    public GestionDeSesion(UsuarioRepositorio usuarios, RefreshTokenRepositorio refreshTokens,
            HasheadorDeContrasena hasheador, EmisorDeAccessToken emisor, GeneradorDeSecretos secretos,
            VersionDeCredenciales versiones, GeneradorDeIds ids, Clock reloj, VigenciasDeSesion vigencias,
            PublicadorDeAuditoria auditoria) {
        this.usuarios = usuarios;
        this.refreshTokens = refreshTokens;
        this.hasheador = hasheador;
        this.emisor = emisor;
        this.secretos = secretos;
        this.versiones = versiones;
        this.ids = ids;
        this.reloj = reloj;
        this.vigencias = vigencias;
        this.auditoria = auditoria;
        this.hashFicticio = hasheador.hashear(UUID.randomUUID().toString());
    }

    @Transactional
    public ResultadoLogin iniciar(String email, String contrasena, String ip) {
        Instant ahora = Instant.now(reloj);
        Optional<Usuario> encontrado = email == null ? Optional.empty() : usuarios.buscarPorEmailParaActualizar(email);
        if (encontrado.isPresent() && encontrado.get().estaBloqueado(ahora)) {
            // Sin evaluar BCrypt ni tocar el contador: los intentos durante el bloqueo no lo extienden (design D1).
            Usuario bloqueado = encontrado.get();
            auditarFallo(bloqueado, ip);
            return new Bloqueada(bloqueado.minutosDeBloqueoRestantes(ahora));
        }
        // Siempre se compara un hash, exista o no el email: el tiempo de respuesta no revela si la cuenta existe.
        String hash = encontrado.map(Usuario::passwordHash).orElse(hashFicticio);
        boolean coincide = hasheador.coincide(contrasena == null ? "" : contrasena, hash);
        if (encontrado.isEmpty()) {
            // Sin evento: no hay consultorio al cual asociarlo (design D9).
            return new CredencialesInvalidas();
        }
        Usuario usuario = encontrado.get();
        if (!coincide) {
            Usuario conFallo = usuario.registrarFallo(ahora);
            usuarios.guardarAcceso(conFallo);
            auditarFallo(conFallo, ip);
            return new CredencialesInvalidas();
        }
        if (usuario.intentosFallidos() > 0 || usuario.bloqueadoHasta() != null) {
            usuarios.guardarAcceso(usuario.registrarExito());
        }
        auditoria.publicar(new CambioAuditado(usuario.consultorio(), new Actor(ActorTipo.ODONTOLOGO, usuario.id()),
                ENTIDAD, usuario.id(), "LOGIN_EXITOSO", null, null, ip));
        return new Exitoso(emitirSesion(usuario.id(), usuario.consultorio(), usuario.credencialesVersion()));
    }

    /** Actor sin id: quien intenta todavía no está autenticado (design D9). */
    private void auditarFallo(Usuario usuario, String ip) {
        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("intentos_fallidos", usuario.intentosFallidos());
        estado.put("bloqueado_hasta", usuario.bloqueadoHasta() == null ? null : usuario.bloqueadoHasta().toString());
        auditoria.publicar(new CambioAuditado(usuario.consultorio(), new Actor(ActorTipo.ODONTOLOGO, null),
                ENTIDAD, usuario.id(), "LOGIN_FALLIDO", null, estado, ip));
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
