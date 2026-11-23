package ar.sistemaodontologico.agenda.application;

import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import java.util.Optional;
import java.util.UUID;

/** Puerto de lectura de la lista y el detalle de pacientes (C-17); filtra por consultorio (regla dura 6). */
public interface ListadoDePacientesRepositorio {

    /** Los pacientes del consultorio por apellido, nombre e id, desde {@code desde} y hasta {@code cantidad}. */
    PaginaDePacientes pagina(ConsultorioId consultorio, boolean soloConRevision, int desde, int cantidad);

    Optional<PacienteDeLaLista> buscar(ConsultorioId consultorio, UUID id);
}
