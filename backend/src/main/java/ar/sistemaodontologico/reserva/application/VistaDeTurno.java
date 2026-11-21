package ar.sistemaodontologico.reserva.application;

import java.time.Instant;

/**
 * Lo que ve el paciente en {@code /t/{token}} (spec gestion-turno-paciente). Nunca lleva la nota, el motivo de
 * cancelación, el DNI, el teléfono o el email del paciente, ni ids internos.
 */
public record VistaDeTurno(TurnoVisible turno, PacienteVisible paciente, ConsultorioVisible consultorio,
        boolean puedeModificar, boolean puedeReprogramar, Instant modificableHasta) {

    public record TurnoVisible(Instant inicio, Instant fin, String prestacion, String estado, String canceladoPor) {
    }

    public record PacienteVisible(String nombre, String apellido) {

        @Override
        public String toString() {
            return "PacienteVisible[***]";
        }
    }

    public record ConsultorioVisible(String nombre, String direccion, String telefono, String zonaHoraria) {
    }
}
