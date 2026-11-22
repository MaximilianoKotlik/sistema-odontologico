import { el } from '../shared/dom';
import { montarHorarios } from '../features/configuracion/horarios';
import { pestanas } from '../features/configuracion/ui';
import { avisarTurnosFueraDeHorario } from '../features/turnos/aviso-fuera-de-horario';
import type { Pagina } from './pagina';

/** Configuración › Horarios (C-09, design D2 y D7), con el aviso de turnos fuera de horario de C-14 (D10). */
export const pagina: Pagina = {
  titulo: 'Configuración',
  seccion: 'configuracion',
  montar(contexto) {
    const cuerpo = el('div', {});
    contexto.contenedor.replaceChildren(pestanas('horarios'), cuerpo);
    montarHorarios(contexto, cuerpo, { despuesDeGuardar: (zona) => void avisarTurnosFueraDeHorario(contexto.http, contexto.zonaHoraria, zona) });
  },
};
