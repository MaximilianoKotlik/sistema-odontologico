import { montarRegistro } from '../features/auditoria/registro';
import type { Pagina } from './pagina';

/** Registro de cambios del consultorio (C-18, spec pantalla-registro-cambios). */
export const pagina: Pagina = {
  titulo: 'Registro de cambios',
  seccion: 'auditoria',
  montar: montarRegistro,
};
