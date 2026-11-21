package ar.sistemaodontologico.agenda.application;

import ar.sistemaodontologico.agenda.domain.Consentimiento;
import ar.sistemaodontologico.agenda.domain.Dni;
import ar.sistemaodontologico.agenda.domain.Paciente;
import ar.sistemaodontologico.agenda.domain.PacienteId;
import ar.sistemaodontologico.agenda.domain.TurnoId;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import java.util.Optional;

/** Puerto de persistencia de los pacientes; todo filtra por consultorio (regla dura 6, design D2 y D6). */
public interface PacienteRepositorio {

    /** Inserta si el DNI no existe en el consultorio ({@code ON CONFLICT DO NOTHING}); dice si insertó. */
    boolean insertarSiNoExiste(ConsultorioId consultorio, Paciente paciente);

    Optional<Paciente> buscar(ConsultorioId consultorio, PacienteId id);

    /** El paciente de ese DNI, con su fila bloqueada hasta el fin de la transacción. */
    Optional<Paciente> buscarPorDniParaActualizar(ConsultorioId consultorio, Dni dni);

    /** Guarda la marca de revisión y lo informado del paciente (DD-38), sin tocar su ficha. */
    void guardarRevision(ConsultorioId consultorio, Paciente paciente);

    /** Completa el turno de origen de lo informado (DD-38). */
    void vincularTurnoDeOrigen(ConsultorioId consultorio, PacienteId paciente, TurnoId turno);

    /** La aceptación vigente de la política de privacidad, renovada en cada reserva por link (C-15, design D7). */
    void actualizarConsentimiento(ConsultorioId consultorio, PacienteId paciente, Consentimiento consentimiento);
}
