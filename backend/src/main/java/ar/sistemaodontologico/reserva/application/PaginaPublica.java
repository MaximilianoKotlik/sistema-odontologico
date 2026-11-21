package ar.sistemaodontologico.reserva.application;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Lo que ve el paciente en {@code /c/{slug}} (spec reserva-publica). {@code reservableHasta} es el último día
 * reservable en la fecha local del consultorio, que pide C-16 (su design D3) para cortar "Ver más días".
 */
public record PaginaPublica(String nombre, String direccion, String telefono, String zonaHoraria,
        boolean tomaTurnosOnline, LocalDate reservableHasta, List<PrestacionPublica> prestaciones) {

    public record PrestacionPublica(UUID id, String nombre, int duracionMin) {
    }
}
