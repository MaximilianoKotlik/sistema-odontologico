package ar.sistemaodontologico.auditoria.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Filtros combinables del registro (C-18, design D6 y D7). {@code desde} y {@code hasta} son fechas locales del
 * consultorio, inclusivas. Llegan como texto para responder 400 {@code VALIDACION} con el campo que falló.
 */
public record FiltrosDeAuditoria(UUID turnoId, LocalDate desde, LocalDate hasta, List<String> acciones) {

    private static final Pattern FORMATO_ACCION = Pattern.compile("^[A-Z][A-Z0-9]*(_[A-Z0-9]+)*$");

    public FiltrosDeAuditoria {
        acciones = acciones == null ? List.of() : List.copyOf(acciones);
        if (desde != null && hasta != null && hasta.isBefore(desde)) {
            throw ErrorDeFiltro.en("hasta");
        }
        if (!acciones.stream().allMatch(accion -> FORMATO_ACCION.matcher(accion).matches())) {
            throw ErrorDeFiltro.en("accion");
        }
    }

    public static FiltrosDeAuditoria desdeTexto(String turnoId, String desde, String hasta, List<String> acciones) {
        return new FiltrosDeAuditoria(uuid(turnoId), fecha(desde, "desde"), fecha(hasta, "hasta"), acciones);
    }

    /** Primer instante del rango en la zona del consultorio; {@code null} sin {@code desde}. */
    public Instant inicio(ZoneId zona) {
        return desde == null ? null : desde.atStartOfDay(zona).toInstant();
    }

    /** Primer instante fuera del rango; {@code null} sin {@code hasta}. */
    public Instant finExclusivo(ZoneId zona) {
        return hasta == null ? null : hasta.plusDays(1).atStartOfDay(zona).toInstant();
    }

    private static UUID uuid(String texto) {
        if (texto == null) {
            return null;
        }
        try {
            return UUID.fromString(texto);
        } catch (IllegalArgumentException e) {
            throw ErrorDeFiltro.en("turnoId");
        }
    }

    private static LocalDate fecha(String texto, String campo) {
        if (texto == null) {
            return null;
        }
        try {
            return LocalDate.parse(texto);
        } catch (DateTimeParseException e) {
            throw ErrorDeFiltro.en(campo);
        }
    }
}
