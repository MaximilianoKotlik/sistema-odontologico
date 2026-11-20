package ar.sistemaodontologico.compartido.infrastructure.configuracion;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Metadatos del contrato OpenAPI que publica springdoc en {@code /v3/api-docs}. */
@Configuration
class ContratoOpenApi {

    @Bean
    OpenAPI contrato() {
        return new OpenAPI().info(new Info()
                .title("API Agenda Odontológica")
                .version("v1")
                .description("API REST de la agenda del consultorio y de la reserva pública por link."));
    }
}
