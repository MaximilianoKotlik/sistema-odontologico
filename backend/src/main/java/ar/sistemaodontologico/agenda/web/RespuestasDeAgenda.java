package ar.sistemaodontologico.agenda.web;

import ar.sistemaodontologico.agenda.application.DetalleDeTurno;
import ar.sistemaodontologico.agenda.application.TurnoDeAgenda;
import ar.sistemaodontologico.agenda.domain.Bloqueo;
import ar.sistemaodontologico.agenda.domain.CanceladoPor;
import ar.sistemaodontologico.agenda.domain.CambioPaciente;
import ar.sistemaodontologico.agenda.domain.EstadoTurno;
import ar.sistemaodontologico.agenda.domain.OrigenTurno;
import java.time.Instant;
import java.util.UUID;

/**
 * DTOs de respuesta de la API de agenda (C-12, design D1, D2 y D8): JSON en {@code camelCase} e instantes en UTC
 * (ISO-8601). La conversión a hora local es del cliente (RN-TE-05).
 */
final class RespuestasDeAgenda {

    private RespuestasDeAgenda() {
    }

    record PrestacionResumen(UUID id, String nombre) {
    }

    record PacienteResumen(UUID id, String nombre, String apellido) {

        @Override
        public String toString() {
            return "PacienteResumen[id=" + id + "]";
        }
    }

    /** Un turno de la agenda, sin datos personales de más (D2). */
    record TurnoResponse(UUID id, Instant inicio, Instant fin, int duracionMin, EstadoTurno estado,
            boolean esSobreturno, OrigenTurno origen, PrestacionResumen prestacion, PacienteResumen paciente,
            boolean tieneNota, CanceladoPor canceladoPor, CambioPaciente cambioPaciente, Instant novedadPacienteAt,
            long version) {

        static TurnoResponse de(TurnoDeAgenda turno) {
            return new TurnoResponse(turno.id(), turno.inicio(), turno.fin(), turno.duracionMin(), turno.estado(),
                    turno.esSobreturno(), turno.origen(),
                    new PrestacionResumen(turno.prestacionId(), turno.prestacionNombre()),
                    new PacienteResumen(turno.pacienteId(), turno.pacienteNombre(), turno.pacienteApellido()),
                    turno.tieneNota(), turno.canceladoPor(), turno.cambioPaciente(), turno.novedadPacienteAt(),
                    turno.version());
        }
    }

    record PacienteDelDetalle(UUID id, String nombre, String apellido, String dni, String telefono, String email,
            boolean requiereRevision) {

        @Override
        public String toString() {
            return "PacienteDelDetalle[id=" + id + "]";
        }
    }

    record Horario(Instant inicio, Instant fin) {
    }

    /** El detalle de un turno (D4): lo de la agenda más los datos de contacto, la nota, el motivo y el horario anterior. */
    record DetalleResponse(UUID id, Instant inicio, Instant fin, int duracionMin, EstadoTurno estado,
            boolean esSobreturno, OrigenTurno origen, PrestacionResumen prestacion, PacienteDelDetalle paciente,
            boolean tieneNota, String notaInterna, CanceladoPor canceladoPor, String motivoCancelacion,
            CambioPaciente cambioPaciente, Instant novedadPacienteAt, Horario horarioAnterior, long version) {

        static DetalleResponse de(DetalleDeTurno detalle) {
            TurnoDeAgenda turno = detalle.turno();
            return new DetalleResponse(turno.id(), turno.inicio(), turno.fin(), turno.duracionMin(), turno.estado(),
                    turno.esSobreturno(), turno.origen(),
                    new PrestacionResumen(turno.prestacionId(), turno.prestacionNombre()),
                    new PacienteDelDetalle(turno.pacienteId(), turno.pacienteNombre(), turno.pacienteApellido(),
                            detalle.dni(), detalle.telefono(), detalle.email(), detalle.requiereRevision()),
                    turno.tieneNota(), detalle.notaInterna(), turno.canceladoPor(), detalle.motivoCancelacion(),
                    turno.cambioPaciente(), turno.novedadPacienteAt(),
                    detalle.horarioAnterior().map(anterior -> new Horario(anterior.inicio(), anterior.fin()))
                            .orElse(null),
                    turno.version());
        }

        @Override
        public String toString() {
            return "DetalleResponse[id=" + id + "]";
        }
    }

    record BloqueoResponse(UUID id, Instant inicio, Instant fin, String motivo) {

        static BloqueoResponse de(Bloqueo bloqueo) {
            return new BloqueoResponse(bloqueo.id().valor(), bloqueo.intervalo().inicio(), bloqueo.intervalo().fin(),
                    bloqueo.motivo());
        }
    }
}
