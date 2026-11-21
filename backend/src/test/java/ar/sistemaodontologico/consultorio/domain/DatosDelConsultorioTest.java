package ar.sistemaodontologico.consultorio.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.sistemaodontologico.compartido.domain.error.ValidacionDeNegocio;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Datos editables del consultorio y su diff para la auditoría (C-08, tareas 3.1 y 3.4; design D4, D9). */
class DatosDelConsultorioTest {

    private static final DatosDelConsultorio BASE = new DatosDelConsultorio("Consultorio Demo", "demo-sur",
            "Av. Siempreviva 742", "+5491100000001", "America/Argentina/Buenos_Aires", ReglasDeReserva.POR_DEFECTO, true);

    @Test
    void zonaHorariaInexistente() {
        assertThatThrownBy(() -> new DatosDelConsultorio("Consultorio Demo", "demo-sur", null, "+5491100000001",
                "America/Ciudad_Inventada", ReglasDeReserva.POR_DEFECTO, true))
                .isInstanceOfSatisfying(ValidacionDeNegocio.class,
                        error -> assertThat(error.getCodigo()).isEqualTo("ZONA_HORARIA_INVALIDA"));
    }

    @Test
    void unaDireccionEnBlancoQuedaVacia() {
        DatosDelConsultorio datos = new DatosDelConsultorio("Consultorio Demo", "demo-sur", "   ", "+5491100000001",
                "America/Argentina/Buenos_Aires", ReglasDeReserva.POR_DEFECTO, true);

        assertThat(datos.direccion()).isNull();
    }

    @Test
    void elMapaAuditableUsaSnakeCase() {
        assertThat(BASE.comoMapaAuditable()).containsOnlyKeys("nombre", "slug", "direccion", "telefono_contacto",
                "zona_horaria", "anticipacion_cancelacion_min", "anticipacion_reserva_min", "horizonte_reserva_dias",
                "paso_huecos_min", "reserva_publica_activa");
    }

    @Test
    void lasDiferenciasTienenSoloLosCamposCambiados() {
        DatosDelConsultorio cambiados = new DatosDelConsultorio("Consultorio Demo", "demo-sur", "Av. Siempreviva 742",
                "+5491100000001", "America/Argentina/Buenos_Aires", new ReglasDeReserva(2880, 120, 60, 15), true);

        Diferencias diferencias = Diferencias.entre(BASE.comoMapaAuditable(), cambiados.comoMapaAuditable());

        assertThat(diferencias.anterior()).isEqualTo(Map.of("anticipacion_cancelacion_min", 1440));
        assertThat(diferencias.nuevo()).isEqualTo(Map.of("anticipacion_cancelacion_min", 2880));
        assertThat(diferencias.hayCambios()).isTrue();
    }

    @Test
    void sinCambiosNoHayDiferencias() {
        assertThat(Diferencias.entre(BASE.comoMapaAuditable(), BASE.comoMapaAuditable()).hayCambios()).isFalse();
    }

    @Test
    void unCampoQuePasaANuloTambienEsUnCambio() {
        DatosDelConsultorio sinDireccion = new DatosDelConsultorio("Consultorio Demo", "demo-sur", null,
                "+5491100000001", "America/Argentina/Buenos_Aires", ReglasDeReserva.POR_DEFECTO, true);

        Diferencias diferencias = Diferencias.entre(BASE.comoMapaAuditable(), sinDireccion.comoMapaAuditable());

        assertThat(diferencias.anterior()).containsEntry("direccion", "Av. Siempreviva 742");
        assertThat(diferencias.nuevo()).containsEntry("direccion", null);
    }
}
