package ar.sistemaodontologico.agenda.application;

import ar.sistemaodontologico.agenda.domain.CanceladoPor;
import ar.sistemaodontologico.agenda.domain.CambioPaciente;
import ar.sistemaodontologico.agenda.domain.EstadoTurno;
import ar.sistemaodontologico.agenda.domain.OrigenTurno;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Un turno como lo muestra la agenda (C-12, design D2): liviano, sin el DNI, el teléfono ni el email del paciente, ni
 * el texto de la nota o del motivo, para no llevar datos personales de más a la caché offline (decisión de Maxi en
 * #27). {@code cambioPaciente} lo completa {@link ConsultarAgenda} desde la auditoría (D3).
 */
public record TurnoDeAgenda(UUID id, Instant inicio, Instant fin, int duracionMin, EstadoTurno estado,
        boolean esSobreturno, OrigenTurno origen, UUID prestacionId, String prestacionNombre, UUID pacienteId,
        String pacienteNombre, String pacienteApellido, boolean tieneNota, CanceladoPor canceladoPor,
        CambioPaciente cambioPaciente, Instant novedadPacienteAt, long version) {

    public TurnoDeAgenda {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(inicio, "inicio");
        Objects.requireNonNull(fin, "fin");
        Objects.requireNonNull(estado, "estado");
    }

    TurnoDeAgenda conCambio(CambioPaciente cambio) {
        return new TurnoDeAgenda(id, inicio, fin, duracionMin, estado, esSobreturno, origen, prestacionId,
                prestacionNombre, pacienteId, pacienteNombre, pacienteApellido, tieneNota, canceladoPor, cambio,
                novedadPacienteAt, version);
    }

    @Override
    public String toString() {
        return "TurnoDeAgenda[id=" + id + ", estado=" + estado + "]";
    }
}
