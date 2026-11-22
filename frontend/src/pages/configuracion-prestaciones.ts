import { el } from '../shared/dom';
import { montarPrestaciones } from '../features/configuracion/prestaciones';
import { pestanas } from '../features/configuracion/ui';
import type { Pagina } from './pagina';

/** Configuración › Prestaciones (C-09, design D2 y D6). */
export const pagina: Pagina = {
  titulo: 'Configuración',
  seccion: 'configuracion',
  montar(contexto) {
    const cuerpo = el('div', {});
    contexto.contenedor.replaceChildren(pestanas('prestaciones'), cuerpo);
    montarPrestaciones(contexto, cuerpo);
  },
};
