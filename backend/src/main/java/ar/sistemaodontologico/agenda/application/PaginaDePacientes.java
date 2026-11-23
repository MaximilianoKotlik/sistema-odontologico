package ar.sistemaodontologico.agenda.application;

import java.util.List;

/** Una página de la lista de pacientes con el total de resultados del filtro (C-17, design D4). */
public record PaginaDePacientes(List<PacienteDeLaLista> items, long total) {
}
