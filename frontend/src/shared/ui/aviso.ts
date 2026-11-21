import { el } from '../dom';
import { icono } from './iconos';

export type TonoAviso = 'info' | 'exito' | 'error';

export interface OpcionesAviso {
  readonly tono?: TonoAviso;
  readonly duracionMs?: number;
}

function region(): HTMLElement {
  const existente = document.querySelector<HTMLElement>('[data-region-avisos]');
  if (existente !== null) return existente;
  const nueva = el('div', {
    'data-region-avisos': true,
    'aria-live': 'polite',
    class: 'fixed inset-x-4 bottom-bajo-nav z-50 flex flex-col items-center gap-2 pointer-events-none md:bottom-5',
  });
  document.body.append(nueva);
  return nueva;
}

/** Aviso breve (toast) que se anuncia a lectores de pantalla y desaparece solo. */
export function aviso(mensaje: string, { tono = 'info', duracionMs = 5000 }: OpcionesAviso = {}): HTMLElement {
  const nodo = el(
    'div',
    {
      role: tono === 'error' ? 'alert' : 'status',
      class:
        'pointer-events-auto flex gap-3 items-start max-w-md w-full bg-texto text-fondo px-4 py-3 rounded-md shadow-md motion-safe:animate-subir',
    },
    icono(tono === 'error' ? 'alerta' : tono === 'exito' ? 'check' : 'reloj', 'w-icono-sm h-icono-sm flex-none mt-1'),
    el('span', {}, mensaje),
  );
  region().append(nodo);
  window.setTimeout(() => nodo.remove(), duracionMs);
  return nodo;
}
