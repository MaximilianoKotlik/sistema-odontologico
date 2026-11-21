package ar.sistemaodontologico.auth.infrastructure;

import ar.sistemaodontologico.auth.infrastructure.seguridad.PropiedadesDeAuth;
import ar.sistemaodontologico.compartido.application.PropiedadesDeMail;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Datos de los emails de autenticación (design D5). */
@Configuration(proxyBeanMethods = false)
class ConfiguracionDeMail {

    @Bean
    PropiedadesDeMail propiedadesDeMail(@Value("${app.mail.from}") String remitente, PropiedadesDeAuth auth) {
        return new PropiedadesDeMail(remitente, auth.frontendOrigin());
    }
}
