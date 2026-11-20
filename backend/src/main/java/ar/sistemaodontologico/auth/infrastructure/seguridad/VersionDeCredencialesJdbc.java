package ar.sistemaodontologico.auth.infrastructure.seguridad;

import ar.sistemaodontologico.auth.application.VersionDeCredenciales;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Una consulta por PK con tenant en cada petición autenticada (design D3; cacheable sin cambiar el contrato). */
@Component
class VersionDeCredencialesJdbc implements VersionDeCredenciales {

    private final JdbcClient jdbc;

    VersionDeCredencialesJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Integer> vigente(ConsultorioId consultorio, UUID usuario) {
        return jdbc.sql("SELECT credenciales_version FROM usuario WHERE consultorio_id = ? AND id = ?")
                .params(consultorio.valor(), usuario)
                .query(Integer.class)
                .optional();
    }
}
