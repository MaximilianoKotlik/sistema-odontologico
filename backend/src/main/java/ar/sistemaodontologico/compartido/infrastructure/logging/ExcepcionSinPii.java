package ar.sistemaodontologico.compartido.infrastructure.logging;

import ar.sistemaodontologico.compartido.domain.EnmascaradorDePii;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.springframework.boot.logging.logback.ExtendedWhitespaceThrowableProxyConverter;

/**
 * Reemplaza a {@code %wEx}/{@code %ex} en {@code logback-spring.xml}: el stack trace sale con los datos
 * personales enmascarados. Las excepciones de Postgres traen valores en el detalle, por ejemplo
 * {@code Key (email)=(...) already exists} (C-03, design D-6).
 */
public class ExcepcionSinPii extends ExtendedWhitespaceThrowableProxyConverter {

    @Override
    public String convert(ILoggingEvent evento) {
        return EnmascaradorDePii.enmascarar(super.convert(evento));
    }
}
