import { montarDetalle } from '../features/agenda/detalle';
import type { Pagina } from './pagina';

/** Detalle de turno (C-13, design D7): una página dentro de la sección Agenda. */
export const pagina: Pagina = {
  titulo: 'Turno',
  seccion: 'agenda',
  montar: montarDetalle,
};
