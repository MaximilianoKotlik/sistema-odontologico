import { montarAsistente } from '../features/configuracion/asistente';
import type { Pagina } from './pagina';

/** Asistente de primer ingreso (C-09, FL-02, design D2 y D3): sin la navegación del shell. */
export const pagina: Pagina = {
  titulo: 'Armemos tu consultorio',
  seccion: null,
  sinShell: true,
  montar: (contexto) => montarAsistente(contexto),
};
