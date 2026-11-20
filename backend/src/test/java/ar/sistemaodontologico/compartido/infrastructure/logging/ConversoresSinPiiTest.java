package ar.sistemaodontologico.compartido.infrastructure.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.Test;

/**
 * Conversores de Logback que enmascaran datos personales en el mensaje y en el stack trace (C-03, tarea 3.2;
 * design D-6). Solo datos ficticios (regla dura 17).
 */
class ConversoresSinPiiTest {

    private static final String TOKEN = "q7Zt_9xYv2LmN0pK-4sDfGhJ8wErTyUi1oPa3bCdE5f";

    private final LoggerContext contexto = new LoggerContext();
    private final Logger logger = contexto.getLogger("prueba");

    @Test
    void elMensajeSaleSinEmailTelefonoNiToken() {
        LoggingEvent evento = evento("paciente {} con tel {} y token {}", null,
                "paciente@example.com", "+54 9 11 0000-0001", TOKEN);

        String salida = new MensajeSinPii().convert(evento);

        assertThat(salida)
                .startsWith("paciente ")
                .doesNotContain("paciente@example.com")
                .doesNotContain("0000-0001")
                .doesNotContain(TOKEN);
    }

    @Test
    void unMensajeSinDatosPersonalesQuedaIgual() {
        LoggingEvent evento = evento("turno {} confirmado el 2026-11-20", null, 42);

        assertThat(new MensajeSinPii().convert(evento)).isEqualTo("turno 42 confirmado el 2026-11-20");
    }

    @Test
    void elStackTraceSaleSinElEmailDelDetalleDeLaBase() {
        RuntimeException causa = new RuntimeException(
                "ERROR: duplicate key value violates unique constraint \"uk_usuario_email\"\n"
                        + "  Detail: Key (email)=(odontologa@example.com) already exists.");
        RuntimeException error = new IllegalStateException("No se pudo registrar al usuario", causa);

        String salida = conversorDeExcepciones().convert(evento("fallo en el alta", error));

        assertThat(salida)
                .contains("IllegalStateException")
                .contains("uk_usuario_email")
                .doesNotContain("odontologa@example.com");
    }

    @Test
    void sinExcepcionNoAgregaNada() {
        assertThat(conversorDeExcepciones().convert(evento("todo bien", null))).isEmpty();
    }

    private ExcepcionSinPii conversorDeExcepciones() {
        ExcepcionSinPii conversor = new ExcepcionSinPii();
        conversor.setContext(contexto);
        conversor.start();
        return conversor;
    }

    private LoggingEvent evento(String mensaje, Throwable error, Object... argumentos) {
        return new LoggingEvent(Logger.class.getName(), logger, Level.ERROR, mensaje, error, argumentos);
    }
}
