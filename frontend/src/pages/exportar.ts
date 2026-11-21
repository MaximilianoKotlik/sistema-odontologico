import { montarExportar } from '../features/exportacion/pagina-exportar';
import type { Pagina } from './pagina';

/** Exportar mis datos (C-19, US-050): reemplaza la sección provisoria de C-06. */
export const pagina: Pagina = {
  titulo: 'Exportar mis datos',
  seccion: 'exportar',
  montar: montarExportar,
};
