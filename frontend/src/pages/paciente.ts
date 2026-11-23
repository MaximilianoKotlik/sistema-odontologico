import { montarRevisionDePaciente } from '../features/pacientes/revision';
import type { Pagina } from './pagina';

/** Revisión de un paciente (C-17, design D8): queda dentro de la sección Pacientes. */
export const pagina: Pagina = {
  titulo: 'Pacientes',
  seccion: 'pacientes',
  montar: montarRevisionDePaciente,
};
