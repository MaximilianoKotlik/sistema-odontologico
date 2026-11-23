import { montarListaDePacientes } from '../features/pacientes/lista';
import type { Pagina } from './pagina';

/** Pacientes (C-17, design D8): lista con el filtro de revisión. */
export const pagina: Pagina = {
  titulo: 'Pacientes',
  seccion: 'pacientes',
  montar: montarListaDePacientes,
};
