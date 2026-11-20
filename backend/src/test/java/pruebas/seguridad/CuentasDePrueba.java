package pruebas.seguridad;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Solo de test (C-04): crea un consultorio con su odontólogo directamente en la base, para obtener tokens con
 * un usuario real sin pasar por el registro. Datos ficticios (regla dura 17).
 */
public final class CuentasDePrueba {

    public record Cuenta(UUID consultorio, UUID usuario) {
    }

    private CuentasDePrueba() {
    }

    public static Cuenta crear(JdbcTemplate jdbc) {
        UUID consultorio = UUID.randomUUID();
        UUID usuario = UUID.randomUUID();
        String sufijo = consultorio.toString().substring(0, 8);
        jdbc.update("INSERT INTO consultorio (id, nombre, slug, plan) VALUES (?, 'Consultorio Demo', ?, 'PRUEBA')",
                consultorio, "cuenta-" + sufijo);
        jdbc.update("""
                INSERT INTO usuario (id, consultorio_id, email, password_hash, nombre, apellido,
                                     password_cambiado_at, terminos_version, terminos_aceptados_at)
                VALUES (?, ?, ?, '$2a$12$hashficticiodeprueba', 'Ana', 'Prueba', now(), '2026-11-borrador', now())""",
                usuario, consultorio, "cuenta-" + sufijo + "@example.com");
        return new Cuenta(consultorio, usuario);
    }
}
