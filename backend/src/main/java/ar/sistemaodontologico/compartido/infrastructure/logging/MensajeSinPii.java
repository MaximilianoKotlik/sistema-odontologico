package ar.sistemaodontologico.compartido.infrastructure.logging;

import ar.sistemaodontologico.compartido.domain.EnmascaradorDePii;
import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Reemplaza a {@code %m}/{@code %msg}/{@code %message} en {@code logback-spring.xml}: el mensaje sale con los
 * emails, teléfonos y tokens enmascarados (C-03, design D-6; RN-GL-01).
 */
public class MensajeSinPii extends MessageConverter {

    @Override
    public String convert(ILoggingEvent evento) {
        return EnmascaradorDePii.enmascarar(super.convert(evento));
    }
}
