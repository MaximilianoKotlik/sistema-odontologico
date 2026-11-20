package ar.sistemaodontologico.auth.infrastructure;

import ar.sistemaodontologico.auth.application.UsuarioRepositorio;
import ar.sistemaodontologico.auth.domain.CredencialesDeUsuario;
import ar.sistemaodontologico.auth.domain.NuevoUsuario;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Usuario con JdbcClient. El email es CITEXT: la búsqueda no distingue mayúsculas. */
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
                SELECT id, consultorio_id, password_hash, credenciales_version FROM usuario WHERE email = :email""")
                .param("email", email.trim())
                .query((fila, n) -> new CredencialesDeUsuario(
                        fila.getObject("id", UUID.class),
                        new ConsultorioId(fila.getObject("consultorio_id", UUID.class)),
                        fila.getString("password_hash"),
                        fila.getInt("credenciales_version")))
                .optional();
    }
}
