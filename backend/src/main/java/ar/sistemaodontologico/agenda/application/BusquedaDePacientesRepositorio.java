package ar.sistemaodontologico.agenda.application;

import ar.sistemaodontologico.agenda.domain.CriterioDeBusqueda;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import java.util.List;

/** Puerto de lectura del buscador de pacientes (C-12, design D7); filtra por consultorio (regla dura 6). */
public interface BusquedaDePacientesRepositorio {

    /** Los pacientes del consultorio que cumplen el criterio, por apellido y nombre, hasta {@code limite}. */
    List<PacienteEncontrado> listarCoincidencias(ConsultorioId consultorio, CriterioDeBusqueda criterio, int limite);
}
