import { el } from '../../shared/dom';
import { icono } from '../../shared/ui/iconos';
import type { CambioLegible } from './catalogo';

/**
 * "Antes → después" compartido por el historial y el registro (mockups de C-18). El lector de pantalla anuncia
 * "Inicio: antes 10:00, después 11:00": la flecha queda oculta y el texto de apoyo es `sr-only`.
 */
export function listaDeCambios(cambios: readonly CambioLegible[]): HTMLDListElement | null {
  if (cambios.length === 0) return null;
  return el(
    'dl',
    { class: 'grid grid-cols-[auto_minmax(0,1fr)] gap-x-3 gap-y-1 mt-2 mb-0 px-3 py-2 bg-superficie-tenue rounded-sm text-sm' },
    ...cambios.flatMap((cambio) => [
      el('dt', { class: 'text-texto-tenue' }, cambio.codigo ? el('code', { class: 'font-mono text-xs' }, cambio.etiqueta) : cambio.etiqueta, el('span', { class: 'sr-only' }, ': ')),
      el(
        'dd',
        { class: 'm-0 flex flex-wrap items-center gap-x-2 gap-y-1 tabular [overflow-wrap:anywhere]' },
        cambio.antes === null ? null : el('span', { class: 'sr-only' }, 'antes '),
        cambio.antes === null ? null : el('span', { class: 'line-through text-texto-tenue' }, cambio.antes),
        cambio.antes === null || cambio.despues === null ? null : icono('flecha', 'w-3 h-3 flex-none text-texto-tenue'),
        cambio.antes === null || cambio.despues === null ? null : el('span', { class: 'sr-only' }, ', después '),
        cambio.despues === null ? null : el('span', { class: 'font-semibold text-texto' }, cambio.despues),
      ),
    ]),
  );
}
