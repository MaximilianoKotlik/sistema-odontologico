package ar.sistemaodontologico.auth.infrastructure;

import ar.sistemaodontologico.auth.application.UsuarioRepositorio;
import ar.sistemaodontologico.auth.domain.CredencialesDeUsuario;
import ar.sistemaodontologico.auth.domain.NuevoUsuario;
import ar.sistemaodontologico.auth.domain.Usuario;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Usuario con JdbcClient. El email es CITEXT: la búsqueda no distingue mayúsculas siempre que el parámetro también
 * sea {@code citext}. Sin el {@code CAST}, el driver lo manda como {@code varchar} y PostgreSQL compara como
 * {@code text}, que sí distingue (C-05).
 */
@Repository
class UsuarioRepositorioJdbc implements UsuarioRepositorio {

    private final JdbcClient jdbc;

    UsuarioRepositorioJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void guardarNuevo(NuevoUsuario usuario) {
        Timestamp registrado = Timestamp.from(usuario.registradoAt());
        jdbc.sql("""
                INSERT INTO usuario (id, consultorio_id, email, password_hash, nombre, apellido,
                                     password_cambiado_at, terminos_version, terminos_aceptados_at)
                VALUES (:id, :consultorio, :email, :hash, :nombre, :apellido, :registrado, :terminos, :registrado)""")
                .param("id", usuario.id())
                .param("consultorio", usuario.consultorio().valor())
                .param("email", usuario.email())
                .param("hash", usuario.passwordHash())
                .param("nombre", usuario.nombre())
                .param("apellido", usuario.apellido())
                .param("registrado", registrado)
                .param("terminos", usuario.terminosVersion())
                .update();
    }

    @Override
    public Optional<CredencialesDeUsuario> buscarPorEmail(String email) {
        return jdbc.sql("""
                SELECT id, consultorio_id, password_hash, credenciales_version FROM usuario WHERE email = CAST(:email AS citext)""")
                .param("email", email.trim())
                .query((fila, n) -> new CredencialesDeUsuario(
                        fila.getObject("id", UUID.class),
                        new ConsultorioId(fila.getObject("consultorio_id", UUID.class)),
                        fila.getString("password_hash"),
                        fila.getInt("credenciales_version")))
                .optional();
    }

    private static final String COLUMNAS_DE_USUARIO = """
            id, consultorio_id, email, password_hash, credenciales_version, intentos_fallidos, bloqueado_hasta,
            password_cambiado_at""";

    @Override
    public Optional<Usuario> buscarPorEmailParaActualizar(String email) {
        return jdbc.sql("SELECT " + COLUMNAS_DE_USUARIO + " FROM usuario WHERE email = CAST(:email AS citext) FOR UPDATE")
                .param("email", email.trim())
                .query((fila, n) -> usuario(fila))
                .optional();
    }

    @Override
    public Optional<Usuario> buscarParaActualizar(ConsultorioId consultorio, UUID id) {
        return jdbc.sql("SELECT " + COLUMNAS_DE_USUARIO
                        + " FROM usuario WHERE consultorio_id = :consultorio AND id = :id FOR UPDATE")
                .param("consultorio", consultorio.valor())
                .param("id", id)
                .query((fila, n) -> usuario(fila))
                .optional();
    }

    @Override
    public void guardarAcceso(Usuario usuario) {
        jdbc.sql("""
                UPDATE usuario SET intentos_fallidos = :intentos, bloqueado_hasta = :bloqueado, updated_at = now()
                WHERE consultorio_id = :consultorio AND id = :id""")
                .param("intentos", usuario.intentosFallidos())
                .param("bloqueado", marca(usuario.bloqueadoHasta()))
                .param("consultorio", usuario.consultorio().valor())
                .param("id", usuario.id())
                .update();
    }

    @Override
    public void guardarContrasena(Usuario usuario) {
        jdbc.sql("""
                UPDATE usuario SET password_hash = :hash, credenciales_version = :version,
                                   password_cambiado_at = :cambiado, intentos_fallidos = :intentos,
                                   bloqueado_hasta = :bloqueado, updated_at = now()
                WHERE consultorio_id = :consultorio AND id = :id""")
                .param("hash", usuario.passwordHash())
                .param("version", usuario.credencialesVersion())
                .param("cambiado", marca(usuario.passwordCambiadoAt()))
                .param("intentos", usuario.intentosFallidos())
                .param("bloqueado", marca(usuario.bloqueadoHasta()))
                .param("consultorio", usuario.consultorio().valor())
                .param("id", usuario.id())
                .update();
    }

    private static Usuario usuario(ResultSet fila) throws SQLException {
        Timestamp bloqueado = fila.getTimestamp("bloqueado_hasta");
        return new Usuario(
                fila.getObject("id", UUID.class),
                new ConsultorioId(fila.getObject("consultorio_id", UUID.class)),
                fila.getString("email"),
                fila.getString("password_hash"),
                fila.getInt("credenciales_version"),
                fila.getInt("intentos_fallidos"),
                bloqueado == null ? null : bloqueado.toInstant(),
                fila.getTimestamp("password_cambiado_at").toInstant());
    }

    /**
     * PostgreSQL guarda microsegundos y <em>redondea</em> lo que sobra: un bloqueo calculado con nanosegundos podía
     * quedar guardado hasta medio microsegundo después y durar "16 minutos". Truncar garantiza que lo guardado
     * nunca termina después de lo calculado.
     */
    private static Timestamp marca(Instant instante) {
        return instante == null ? null : Timestamp.from(instante.truncatedTo(ChronoUnit.MICROS));
    }
}
