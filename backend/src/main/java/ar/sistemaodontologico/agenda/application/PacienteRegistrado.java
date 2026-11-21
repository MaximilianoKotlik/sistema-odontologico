package ar.sistemaodontologico.agenda.application;

import ar.sistemaodontologico.agenda.domain.Paciente;
import java.util.Objects;

/**
 * Resultado de {@link RegistrarPaciente#registrar}: el paciente con los datos guardados, si ya existía con ese DNI
 * (C-14 lo muestra: "Ya tenés a … con ese DNI") y si este registro lo marcó para revisión (design D6).
 */
public record PacienteRegistrado(Paciente paciente, boolean reutilizado, boolean marcadoParaRevision) {

    public PacienteRegistrado {
        Objects.requireNonNull(paciente, "paciente");
    }
}
