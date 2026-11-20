package ar.sistemaodontologico.auth.infrastructure;

import ar.sistemaodontologico.auth.application.HasheadorDeContrasena;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/** BCrypt de costo 12 (RN-AU-01). */
@Component
class HasheadorBcrypt implements HasheadorDeContrasena {

    static final int COSTO = 12;

    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(COSTO);

    @Override
    public String hashear(String contrasena) {
        return bcrypt.encode(contrasena);
    }

    @Override
    public boolean coincide(String contrasena, String hash) {
        return contrasena != null && bcrypt.matches(contrasena, hash);
    }
}
