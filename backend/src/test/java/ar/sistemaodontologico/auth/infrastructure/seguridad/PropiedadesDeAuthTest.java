package ar.sistemaodontologico.auth.infrastructure.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/** Design D2 (C-04): el arranque falla si el secreto JWT falta, no es base64 o tiene menos de 32 bytes. */
class PropiedadesDeAuthTest {

    private static final String SECRETO_VALIDO = Base64.getEncoder().encodeToString(new byte[48]);

    @Configuration
    @EnableConfigurationProperties(PropiedadesDeAuth.class)
    static class SoloPropiedades {
    }

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withUserConfiguration(SoloPropiedades.class)
            .withPropertyValues("app.auth.frontend-origin=http://localhost:5173");

    @Test
    void conUnSecretoValidoArrancaConLosValoresPorDefecto() {
        contexto.withPropertyValues("app.auth.jwt-secreto=" + SECRETO_VALIDO).run(arranque -> {
            assertThat(arranque).hasNotFailed();
            PropiedadesDeAuth propiedades = arranque.getBean(PropiedadesDeAuth.class);
            assertThat(propiedades.accessTtl()).isEqualTo(Duration.ofMinutes(15));
            assertThat(propiedades.refreshTtl()).isEqualTo(Duration.ofDays(14));
            assertThat(propiedades.terminosVersionVigente()).isEqualTo("2026-11-borrador");
            assertThat(propiedades.claveJwt()).hasSize(48);
        });
    }

    @Test
    void sinSecretoNoArranca() {
        contexto.run(arranque -> assertThat(arranque).hasFailed()
                .getFailure().rootCause().hasMessageContaining("JWT_SECRET"));
    }

    @Test
    void unSecretoQueNoEsBase64NoArranca() {
        contexto.withPropertyValues("app.auth.jwt-secreto=cambiar-por-un-secreto-de-al-menos-32-bytes!!")
                .run(arranque -> assertThat(arranque).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("base64"));
    }

    @Test
    void unSecretoDeMenosDe32BytesNoArranca() {
        String corto = Base64.getEncoder().encodeToString(new byte[31]);

        contexto.withPropertyValues("app.auth.jwt-secreto=" + corto)
                .run(arranque -> assertThat(arranque).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("32 bytes"));
    }

    @Test
    void elErrorNuncaIncluyeElSecreto() {
        String corto = Base64.getEncoder().encodeToString("secreto-corto".getBytes());

        contexto.withPropertyValues("app.auth.jwt-secreto=" + corto)
                .run(arranque -> assertThat(arranque.getStartupFailure()).hasStackTraceContaining("32 bytes")
                        .satisfies(error -> assertThat(stack(error)).doesNotContain(corto)));
    }

    private static String stack(Throwable error) {
        java.io.StringWriter salida = new java.io.StringWriter();
        error.printStackTrace(new java.io.PrintWriter(salida));
        return salida.toString();
    }
}
