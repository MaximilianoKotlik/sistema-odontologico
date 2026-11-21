package ar.sistemaodontologico.consultorio.domain;

import ar.sistemaodontologico.compartido.domain.error.ValidacionDeNegocio;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Datos editables del consultorio (spec configuracion-consultorio). El plan y {@code prueba_hasta} no están: no se
 * editan por esta vía (C-20). El teléfono ya viene normalizado a E.164; puede faltar en un consultorio recién
 * registrado, pero la API lo exige al guardar (decisión de Maxi en #15).
 */
public record DatosDelConsultorio(String nombre, String slug, String direccion, String telefonoContacto,
        String zonaHoraria, ReglasDeReserva reglas, boolean reservaPublicaActiva) {

    public static final String CODIGO_ZONA_INVALIDA = "ZONA_HORARIA_INVALIDA";

    public DatosDelConsultorio {
        Objects.requireNonNull(nombre, "nombre");
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(reglas, "reglas");
        direccion = direccion == null || direccion.isBlank() ? null : direccion.trim();
        validarZona(zonaHoraria);
    }

    /** Campos en {@code snake_case}, como columnas, para el diff de la auditoría (design D4). */
    public Map<String, Object> comoMapaAuditable() {
        Map<String, Object> campos = new LinkedHashMap<>();
        campos.put("nombre", nombre);
        campos.put("slug", slug);
        campos.put("direccion", direccion);
        campos.put("telefono_contacto", telefonoContacto);
        campos.put("zona_horaria", zonaHoraria);
        campos.put("anticipacion_cancelacion_min", reglas.anticipacionCancelacionMin());
        campos.put("anticipacion_reserva_min", reglas.anticipacionReservaMin());
        campos.put("horizonte_reserva_dias", reglas.horizonteReservaDias());
        campos.put("paso_huecos_min", reglas.pasoHuecosMin());
        campos.put("reserva_publica_activa", reservaPublicaActiva);
        return campos;
    }

    private static void validarZona(String zona) {
        try {
            ZoneId.of(Objects.requireNonNull(zona, "zonaHoraria"));
        } catch (DateTimeException e) {
            throw new ValidacionDeNegocio(CODIGO_ZONA_INVALIDA, "La zona horaria no existe");
        }
    }
}
