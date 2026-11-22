import { montarBloqueos } from '../features/bloqueos/pantalla';
import type { Pagina } from './pagina';

/** Bloqueos (C-14, design D8): reemplaza la sección provisoria de C-06. */
export const pagina: Pagina = {
  titulo: 'Bloqueos',
  seccion: 'bloqueos',
  montar: montarBloqueos,
};
