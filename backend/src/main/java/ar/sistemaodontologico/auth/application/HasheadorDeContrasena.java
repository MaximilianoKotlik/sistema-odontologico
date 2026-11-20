package ar.sistemaodontologico.auth.application;

/** Puerto: hash de contraseñas (BCrypt costo 12, RN-AU-01). */
public interface HasheadorDeContrasena {

    String hashear(String contrasena);

    boolean coincide(String contrasena, String hash);
}
