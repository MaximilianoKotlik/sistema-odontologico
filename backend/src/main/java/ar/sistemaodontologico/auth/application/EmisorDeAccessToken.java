package ar.sistemaodontologico.auth.application;

import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import java.time.Duration;
import java.util.UUID;

/** Puerto: emite el access token JWT de la sesión (design D2). El dominio no conoce Nimbus. */
public interface EmisorDeAccessToken {

    record AccessToken(String valor, Duration vigencia) {

        @Override
        public String toString() {
            return "AccessToken[vigencia=" + vigencia + "]";
        }
    }

    AccessToken emitir(UUID usuario, ConsultorioId consultorio, int versionDeCredenciales);
}
