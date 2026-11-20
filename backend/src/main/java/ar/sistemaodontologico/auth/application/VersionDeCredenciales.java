package ar.sistemaodontologico.auth.application;

import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import java.util.Optional;
import java.util.UUID;

/** Puerto: versión vigente de las credenciales de un usuario, para invalidar tokens viejos (C-04, design D3). */
public interface VersionDeCredenciales {

    /** Vacío si el usuario no existe o no pertenece a ese consultorio. */
    Optional<Integer> vigente(ConsultorioId consultorio, UUID usuario);
}
