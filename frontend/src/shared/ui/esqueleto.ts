import { el } from '../dom';

export interface OpcionesEsqueleto {
  readonly etiqueta?: string;
  readonly filas?: number;
}

/**
 * Esqueleto de carga anunciado como ocupado (spec app-shell: Estado de carga).
 * El brillo usa `motion-safe:`: con prefers-reduced-motion se muestra quieto.
 */
export function esqueleto({ etiqueta = 'Cargando…', filas = 4 }: OpcionesEsqueleto = {}): HTMLElement {
  const bloque = (alto: string) => el('div', { class: `esqueleto ${alto} motion-safe:animate-brillo` });
  const lista = el(
    'div',
    { class: 'grid gap-3', 'aria-hidden': 'true' },
    ...Array.from({ length: filas }, (_, i) =>
      el('div', { class: 'grid grid-cols-[calc(var(--space-7)+var(--space-1))_1fr] gap-3 items-center' }, bloque('h-3'), bloque(i === 1 ? 'h-[calc(var(--space-7)*2)]' : 'h-destino-nav')),
    ),
  );
  return el(
    'div',
    { 'aria-busy': 'true', 'aria-live': 'polite', class: 'bg-superficie border border-borde rounded-lg shadow-sm p-4' },
    el('span', { class: 'sr-only' }, etiqueta),
    lista,
  );
}
