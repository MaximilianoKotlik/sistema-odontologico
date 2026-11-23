import { montarAgenda } from '../features/agenda/pantalla';
import { registrarAccionesDeTurnos } from '../features/turnos/puntos';
import type { Pagina } from './pagina';

/** Agenda diaria y semanal (C-13) con las acciones de turnos de C-14. */
export const pagina: Pagina = {
  titulo: 'Agenda',
  seccion: 'agenda',
  montar: (contexto) => {
    registrarAccionesDeTurnos();
    montarAgenda(contexto);
  },
};
