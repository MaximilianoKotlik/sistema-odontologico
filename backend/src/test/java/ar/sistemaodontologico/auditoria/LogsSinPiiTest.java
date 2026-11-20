package ar.sistemaodontologico.auditoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.sistemaodontologico.compartido.IntegracionPostgresTest;
import ar.sistemaodontologico.compartido.application.PublicadorDeAuditoria;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import ar.sistemaodontologico.compartido.domain.auditoria.Actor;
import ar.sistemaodontologico.compartido.domain.auditoria.CambioAuditado;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Logs sin datos personales con la configuración real de {@code logback-spring.xml} (C-03, tareas 3.3 y 3.4;
 * design D-6). Spec auditoria — Requirement: Datos personales enmascarados en logs. Solo datos ficticios.
 */
@ExtendWith(OutputCaptureExtension.class)
class LogsSinPiiTest extends IntegracionPostgresTest {

    @Autowired
    private PublicadorDeAuditoria publicador;

    @Autowired
    private TransactionTemplate transaccion;

    @Test
    void unaExcepcionDeLaBaseNoMuestraElEmailNiEnElMensajeNiEnElStackTrace(CapturedOutput salida) {
        DataIntegrityViolationException error = new DataIntegrityViolationException(
                "No se pudo registrar a odontologa@example.com",
                new RuntimeException("ERROR: duplicate key value violates unique constraint \"uk_usuario_email\"\n"
                        + "  Detail: Key (email)=(odontologa@example.com) already exists."));

        LoggerFactory.getLogger(LogsSinPiiTest.class).error("Fallo en el alta de odontologa@example.com", error);

        assertThat(salida.getOut())
                .contains("Fallo en el alta de")
                .contains("DataIntegrityViolationException")
                .contains("uk_usuario_email")
                .doesNotContain("odontologa@example.com");
    }

    @Test
    void siFallaElRegistroElLogNombraLaAccionYLaEntidadPeroNoLosValores(CapturedOutput salida) {
        ConsultorioId inexistente = new ConsultorioId(UUID.randomUUID());
        UUID turno = UUID.randomUUID();

        assertThatThrownBy(() -> transaccion.executeWithoutResult(estado -> publicador.publicar(
                new CambioAuditado(inexistente, Actor.sistema(), "turno", turno, "TURNO_NOTA_CAMBIADA",
                        Map.of("nota_interna", "nota-anterior-marcador"),
                        Map.of("nota_interna", "nota-nueva-marcador"), null))))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(salida.getOut())
                .contains("TURNO_NOTA_CAMBIADA")
                .contains("turno")
                .contains(turno.toString())
                .doesNotContain("nota-anterior-marcador")
                .doesNotContain("nota-nueva-marcador");
    }
}
