import { montarAgenda } from '../features/agenda/pantalla';
import type { Pagina } from './pagina';

/** Agenda diaria y semanal (C-13): reemplaza la sección provisoria de C-06. */
export const pagina: Pagina = {
  titulo: 'Agenda',
  seccion: 'agenda',
  montar: montarAgenda,
};
