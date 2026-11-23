import { montarDetalle } from '../features/agenda/detalle';
import { registrarAccionesDeTurnos } from '../features/turnos/puntos';
import type { Pagina } from './pagina';

/** Detalle de turno (C-13, design D7) con las acciones de C-14: una página dentro de la sección Agenda. */
export const pagina: Pagina = {
  titulo: 'Turno',
  seccion: 'agenda',
  montar: (contexto) => {
    registrarAccionesDeTurnos();
    montarDetalle(contexto);
  },
};
