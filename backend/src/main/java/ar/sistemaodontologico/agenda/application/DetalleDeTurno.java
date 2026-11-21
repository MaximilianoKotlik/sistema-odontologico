package ar.sistemaodontologico.agenda.application;

import ar.sistemaodontologico.agenda.domain.Intervalo;
import java.util.Objects;
import java.util.Optional;

/**
 * El detalle completo de un turno para la pantalla de C-13 (C-12, design D4): lo de la agenda más los datos de
 * contacto del paciente, la nota, el motivo de cancelación y el horario anterior si el paciente lo reprogramó (D3).
 * Ningún dato aparece en {@link #toString()} (RN-GL-01).
 */
public record DetalleDeTurno(TurnoDeAgenda turno, String dni, String telefono, String email,
        boolean requiereRevision, String notaInterna, String motivoCancelacion, Optional<Intervalo> horarioAnterior) {

    public DetalleDeTurno {
        Objects.requireNonNull(turno, "turno");
        Objects.requireNonNull(horarioAnterior, "horarioAnterior");
    }

    DetalleDeTurno con(TurnoDeAgenda conCambio, Optional<Intervalo> anterior) {
        return new DetalleDeTurno(conCambio, dni, telefono, email, requiereRevision, notaInterna, motivoCancelacion,
                anterior);
    }

    @Override
    public String toString() {
        return "DetalleDeTurno[id=" + turno.id() + "]";
    }
}
