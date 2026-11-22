package ar.sistemaodontologico.auditoria.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.sistemaodontologico.compartido.domain.error.ValidacionDeNegocio;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Filtros del registro (C-18, tareas 2.1 y 2.3; design D7). */
class FiltrosDeAuditoriaTest {

    private static final ZoneId BUENOS_AIRES = ZoneId.of("America/Argentina/Buenos_Aires");

    @Test
    void sinFiltrosEsValido() {
        FiltrosDeAuditoria filtros = FiltrosDeAuditoria.desdeTexto(null, null, null, null);

        assertThat(filtros.turnoId()).isNull();
        assertThat(filtros.acciones()).isEmpty();
        assertThat(filtros.inicio(BUENOS_AIRES)).isNull();
        assertThat(filtros.finExclusivo(BUENOS_AIRES)).isNull();
    }

    @Test
    void leeTodosLosFiltros() {
        UUID turno = UUID.randomUUID();

        FiltrosDeAuditoria filtros = FiltrosDeAuditoria.desdeTexto(turno.toString(), "2026-11-10", "2026-11-12",
                List.of("TURNO_CREADO", "LOGIN_FALLIDO"));

        assertThat(filtros.turnoId()).isEqualTo(turno);
        assertThat(filtros.desde()).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(filtros.hasta()).isEqualTo(LocalDate.of(2026, 11, 12));
        assertThat(filtros.acciones()).containsExactly("TURNO_CREADO", "LOGIN_FALLIDO");
    }

    @Test
    void elRangoIncluyeElDiaLocalCompleto() {
        FiltrosDeAuditoria filtros = FiltrosDeAuditoria.desdeTexto(null, "2026-11-10", "2026-11-10", null);

        assertThat(filtros.inicio(BUENOS_AIRES)).isEqualTo(Instant.parse("2026-11-10T03:00:00Z"));
        assertThat(filtros.finExclusivo(BUENOS_AIRES)).isEqualTo(Instant.parse("2026-11-11T03:00:00Z"));
    }

    @Test
    void rangoInvertidoMarcaHasta() {
        assertThatThrownBy(() -> FiltrosDeAuditoria.desdeTexto(null, "2026-11-20", "2026-11-10", null))
                .satisfies(error -> assertThat(campo(error)).isEqualTo("hasta"));
    }

    @Test
    void fechaMalFormadaMarcaSuCampo() {
        assertThatThrownBy(() -> FiltrosDeAuditoria.desdeTexto(null, "10/11/2026", null, null))
                .satisfies(error -> assertThat(campo(error)).isEqualTo("desde"));
        assertThatThrownBy(() -> FiltrosDeAuditoria.desdeTexto(null, null, "2026-13-01", null))
                .satisfies(error -> assertThat(campo(error)).isEqualTo("hasta"));
    }

    @Test
    void turnoQueNoEsUuidMarcaSuCampo() {
        assertThatThrownBy(() -> FiltrosDeAuditoria.desdeTexto("no-es-uuid", null, null, null))
                .satisfies(error -> assertThat(campo(error)).isEqualTo("turnoId"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"turno_creado", "TURNO-CREADO", "_TURNO", "TURNO_", ""})
    void accionConFormatoInvalidoMarcaSuCampo(String accion) {
        assertThatThrownBy(() -> FiltrosDeAuditoria.desdeTexto(null, null, null, List.of(accion)))
                .satisfies(error -> assertThat(campo(error)).isEqualTo("accion"));
    }

    @SuppressWarnings("unchecked")
    private static String campo(Throwable error) {
        assertThat(error).isInstanceOf(ValidacionDeNegocio.class);
        ValidacionDeNegocio validacion = (ValidacionDeNegocio) error;
        assertThat(validacion.getCodigo()).isEqualTo("VALIDACION");
        List<Map<String, String>> errores = (List<Map<String, String>>) validacion.getPropiedades().get("errores");
        return errores.getFirst().get("campo");
    }
}
