import { el } from '../shared/dom';
import { montarHorarios } from '../features/configuracion/horarios';
import { pestanas } from '../features/configuracion/ui';
import type { Pagina } from './pagina';

/** Configuración › Horarios (C-09, design D2 y D7). */
export const pagina: Pagina = {
  titulo: 'Configuración',
  seccion: 'configuracion',
  montar(contexto) {
    const cuerpo = el('div', {});
    contexto.contenedor.replaceChildren(pestanas('horarios'), cuerpo);
    montarHorarios(contexto, cuerpo);
  },
};
