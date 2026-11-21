package ar.sistemaodontologico.consultorio.domain;

import ar.sistemaodontologico.compartido.domain.error.ValidacionDeNegocio;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Tipo de prestación del consultorio, con su duración (US-010, spec prestaciones). No se borra: se desactiva
 * (RN-AG-10). Cambiar la duración no altera turnos existentes, que guardan la suya (RN-AG-02).
 */
public record TipoDePrestacion(UUID id, String nombre, int duracionMin, boolean activa, boolean visiblePublica,
        int orden) {

    public static final String CODIGO_DURACION_INVALIDA = "DURACION_INVALIDA";
    public static final int DURACION_MINIMA = 5;
    public static final int DURACION_MAXIMA = 480;
    public static final int LARGO_MAXIMO_NOMBRE = 80;

    public TipoDePrestacion {
        Objects.requireNonNull(id, "id");
        nombre = Objects.requireNonNull(nombre, "nombre").trim();
        if (nombre.isEmpty() || nombre.length() > LARGO_MAXIMO_NOMBRE) {
            throw new ValidacionDeNegocio("VALIDACION", "Hay datos inválidos");
        }
        // RN-AG-08: múltiplos de 5 entre 5 y 480 minutos.
        if (duracionMin < DURACION_MINIMA || duracionMin > DURACION_MAXIMA || duracionMin % 5 != 0) {
            throw new ValidacionDeNegocio(CODIGO_DURACION_INVALIDA,
                    "La duración tiene que ser de 5 a 480 minutos, en múltiplos de 5");
        }
    }

    /** Una prestación recién creada: activa. */
    public static TipoDePrestacion nueva(UUID id, String nombre, int duracionMin, boolean visiblePublica, int orden) {
        return new TipoDePrestacion(id, nombre, duracionMin, true, visiblePublica, orden);
    }

    public TipoDePrestacion editada(String nuevoNombre, int nuevaDuracion, boolean nuevaVisibilidad, int nuevoOrden) {
        return new TipoDePrestacion(id, nuevoNombre, nuevaDuracion, activa, nuevaVisibilidad, nuevoOrden);
    }

    public TipoDePrestacion desactivada() {
        return new TipoDePrestacion(id, nombre, duracionMin, false, visiblePublica, orden);
    }

    public TipoDePrestacion activada() {
        return new TipoDePrestacion(id, nombre, duracionMin, true, visiblePublica, orden);
    }

    /** Campos en {@code snake_case}, como columnas, para la auditoría (design D4). */
    public Map<String, Object> comoMapaAuditable() {
        Map<String, Object> campos = new LinkedHashMap<>();
        campos.put("nombre", nombre);
        campos.put("duracion_min", duracionMin);
        campos.put("visible_publica", visiblePublica);
        campos.put("orden", orden);
        campos.put("activa", activa);
        return campos;
    }
}
