package ar.sistemaodontologico.auth.application;

import ar.sistemaodontologico.auth.domain.CredencialesDeUsuario;
import ar.sistemaodontologico.auth.domain.NuevoUsuario;
import java.util.Optional;

/**
 * Puerto de persistencia del odontólogo. {@link #buscarPorEmail} es la única consulta sin consultorio: el login
 * todavía no tiene tenant y el email es único en todo el sistema (RN-TE-02, design D9).
 */
public interface UsuarioRepositorio {

    void guardarNuevo(NuevoUsuario usuario);

    Optional<CredencialesDeUsuario> buscarPorEmail(String email);
}
