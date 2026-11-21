package ar.sistemaodontologico.exportacion.application;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/** Lo que muestra {@code resumen.pdf} (C-19, design D6). Nunca lleva credenciales ni tokens. */
public record DatosDelResumen(
        String consultorio,
        String direccion,
        String telefono,
        ZoneId zona,
        Instant generadoAt,
        String versionEsquema,
        List<PrestacionResumen> prestaciones,
        List<PacienteResumen> pacientes,
        List<TurnoResumen> turnos,
        AuditoriaResumen auditoria) {

    public record PrestacionResumen(String nombre, int duracionMin, boolean activa, boolean visible) {
    }

    public record PacienteResumen(String apellido, String nombre, String dni, String telefono, String email,
            boolean requiereRevision) {
    }

    public record TurnoResumen(Instant inicio, String paciente, String prestacion, String estado, String origen,
            boolean sobreturno) {
    }

    /** Cantidad por acción y rango de fechas: los eventos uno por uno están en {@code auditoria.csv}. */
    public record AuditoriaResumen(Map<String, Long> porAccion, Instant primero, Instant ultimo) {
    }
}
