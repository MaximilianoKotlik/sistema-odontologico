import { el } from '../../shared/dom';
import { icono } from '../../shared/ui/iconos';
import type { Fila } from './modelo';
import { tarjetaBloqueo, tarjetaTurno } from './tarjeta';

/**
 * Línea de tiempo del día (C-13, design D3): lista ordenada con turnos, bloqueos, huecos libres, tramos sin atención
 * y la marca de ahora. Cada fila tiene su propio alto, así todo es tocable a 360 px.
 */

export interface OpcionesLinea {
  /** Punto de entrada de C-14 para crear un turno en el hueco elegido (hora local `HH:mm`). */
  readonly alElegirLibre: (desde: string) => void;
}

const FILA = 'grid grid-cols-[calc(var(--space-7)+var(--space-1))_minmax(0,1fr)] gap-2';

/**
 * Columna de horas. En los turnos se oculta a lectores de pantalla: la tarjeta ya las nombra (D9). En los bloqueos
 * es lo único que dice el horario, así que se lee "15:00 a 16:00".
 */
function columnaHora(desde: string, hasta: string, destacada: boolean, oculta: boolean): HTMLElement {
  return el(
    'span',
    { 'data-hora': true, 'aria-hidden': oculta ? 'true' : null, class: 'pt-2 text-right tabular' },
    destacada ? el('b', { class: 'block text-base' }, desde) : el('span', { class: 'block text-xs text-texto-tenue' }, desde),
    destacada ? el('span', { class: 'sr-only' }, ' a ') : null,
    destacada ? el('span', { class: 'block text-xs text-texto-tenue' }, hasta) : null,
  );
}

function filaDe(fila: Fila, opciones: OpcionesLinea): HTMLLIElement {
  switch (fila.tipo) {
    case 'turno':
      return el('li', { class: FILA }, columnaHora(fila.desde, fila.hasta, true, true), tarjetaTurno(fila));
    case 'bloqueo':
      return el('li', { class: FILA }, columnaHora(fila.desde, fila.hasta, true, false), tarjetaBloqueo(fila));
    case 'libre': {
      const boton = el(
        'button',
        {
          type: 'button',
          'data-libre': fila.desde,
          'data-requiere-conexion': true,
          class:
            'flex items-center gap-2 w-full min-h-touch px-3 rounded-md border-control border-dashed border-borde-fuerte bg-transparent ' +
            'font-sans text-sm text-texto-tenue text-left cursor-pointer hover:border-primario hover:text-primario ' +
            'disabled:opacity-60 disabled:cursor-not-allowed disabled:hover:border-borde-fuerte disabled:hover:text-texto-tenue',
        },
        icono('mas', 'w-icono-sm h-icono-sm flex-none text-primario'),
        el('span', {}, el('b', { class: 'font-semibold text-texto' }, 'Libre'), ` · ${fila.desde} a ${fila.hasta}`),
      );
      boton.addEventListener('click', () => opciones.alElegirLibre(fila.desde));
      return el('li', { class: FILA }, columnaHora(fila.desde, fila.hasta, false, true), boton);
    }
    case 'fuera':
      return el(
        'li',
        { class: FILA },
        columnaHora(fila.desde, fila.hasta, false, true),
        el(
          'span',
          { 'data-fuera': true, class: 'flex items-center gap-2 min-h-6 px-3 rounded-md bg-superficie-tenue text-sm text-texto-tenue' },
          icono('luna', 'w-icono-sm h-icono-sm flex-none'),
          `No atendés · ${fila.desde} a ${fila.hasta}`,
        ),
      );
    case 'ahora':
      return el(
        'li',
        { class: `${FILA} items-center` },
        el('span', { 'aria-hidden': 'true', class: 'justify-self-end w-2 h-2 -mr-1 rounded-full bg-primario' }),
        el(
          'span',
          { 'data-ahora': true, class: 'flex items-center gap-2 text-xs font-bold text-primario' },
          `Ahora · ${fila.hora}`,
          el('span', { 'aria-hidden': 'true', class: 'flex-1 h-indicador rounded-full bg-primario' }),
        ),
      );
  }
}

export function lineaDeTiempo(filas: readonly Fila[], opciones: OpcionesLinea): HTMLOListElement {
  return el('ol', { 'data-linea-de-tiempo': true, class: 'list-none m-0 p-0 grid gap-2' }, ...filas.map((f) => filaDe(f, opciones)));
}
