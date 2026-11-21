import { el } from '../shared/dom';
import { montarConsultorio } from '../features/configuracion/consultorio';
import { pestanas } from '../features/configuracion/ui';
import type { Pagina } from './pagina';

/** Configuración › Consultorio (C-09, design D2): reemplaza la sección provisoria de C-06. */
export const pagina: Pagina = {
  titulo: 'Configuración',
  seccion: 'configuracion',
  montar(contexto) {
    const cuerpo = el('div', { class: 'pb-7' });
    contexto.contenedor.replaceChildren(pestanas('consultorio'), cuerpo);
    montarConsultorio(contexto, cuerpo);
  },
};
