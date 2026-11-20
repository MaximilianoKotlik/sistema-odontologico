import { el, enfocables, idUnico } from '../dom';

export interface OpcionesDialogo {
  readonly titulo: string;
  readonly descripcion?: string;
  readonly contenido?: Node;
  readonly acciones: readonly HTMLElement[];
  readonly alCerrar?: () => void;
  /** Presentación: centrado (por defecto) u hoja inferior (menú "Más" en mobile). */
  readonly forma?: 'centrado' | 'hoja';
  /** Clases extra del contenedor (p. ej. ocultarlo desde md). */
  readonly claseContenedor?: string;
}

export interface Dialogo {
  readonly abierto: () => boolean;
  abrir(): void;
  cerrar(): void;
}

/**
 * Diálogo modal accesible (design D4): role="dialog", aria-modal, título asociado,
 * foco atrapado, Escape y velo cierran, y el foco vuelve a quien lo abrió.
 * Se usa un div con roles ARIA porque <dialog>.showModal() no está disponible en jsdom.
 */
export function dialogo(opciones: OpcionesDialogo): Dialogo {
  const idTitulo = idUnico('dialogo-titulo');
  const idDescripcion = opciones.descripcion === undefined ? null : idUnico('dialogo-desc');
  const esHoja = opciones.forma === 'hoja';
  let contenedor: HTMLDivElement | null = null;
  let previo: HTMLElement | null = null;

  function alTeclear(evento: KeyboardEvent): void {
    if (contenedor === null) return;
    if (evento.key === 'Escape') {
      evento.preventDefault();
      cerrar();
      return;
    }
    if (evento.key !== 'Tab') return;
    const lista = enfocables(contenedor);
    const primero = lista[0];
    const ultimo = lista.at(-1);
    if (primero === undefined || ultimo === undefined) {
      evento.preventDefault();
      return;
    }
    if (!evento.shiftKey && document.activeElement === ultimo) {
      evento.preventDefault();
      primero.focus();
    } else if (evento.shiftKey && document.activeElement === primero) {
      evento.preventDefault();
      ultimo.focus();
    }
  }

  function abrir(): void {
    if (contenedor !== null) return;
    previo = document.activeElement instanceof HTMLElement ? document.activeElement : null;

    const caja = el(
      'div',
      {
        role: 'dialog',
        'aria-modal': 'true',
        'aria-labelledby': idTitulo,
        'aria-describedby': idDescripcion,
        tabindex: '-1',
        class: esHoja
          ? 'fixed inset-x-0 bottom-0 z-50 bg-superficie rounded-t-lg shadow-lg px-4 pt-2 pb-4 motion-safe:animate-subir'
          : 'fixed left-1/2 top-1/2 z-50 w-[calc(100%-var(--space-6))] max-w-md -translate-x-1/2 -translate-y-1/2 bg-superficie border border-borde rounded-lg shadow-lg p-5',
      },
      esHoja ? el('div', { 'aria-hidden': 'true', class: 'w-6 h-1 rounded-full bg-borde-fuerte mx-auto mt-1 mb-3' }) : null,
      el('h2', { id: idTitulo, class: esHoja ? 'm-0 mb-2 text-base font-semibold text-texto-tenue' : 'm-0 mb-2 text-lg font-bold' }, opciones.titulo),
      idDescripcion === null ? null : el('p', { id: idDescripcion, class: 'm-0 mb-4 text-texto-tenue' }, opciones.descripcion),
      opciones.contenido ?? null,
      opciones.acciones.length === 0 ? null : el('div', { class: 'flex flex-wrap gap-2 justify-end' }, ...opciones.acciones),
    );
    const velo = el('div', { 'data-velo': true, 'aria-hidden': 'true', class: 'fixed inset-0 z-40 bg-velo' });
    velo.addEventListener('click', cerrar);

    contenedor = el('div', { class: opciones.claseContenedor ?? null }, velo, caja);
    contenedor.addEventListener('keydown', alTeclear);
    document.body.append(contenedor);
    (enfocables(caja)[0] ?? caja).focus();
  }

  function cerrar(): void {
    if (contenedor === null) return;
    contenedor.remove();
    contenedor = null;
    previo?.focus();
    previo = null;
    opciones.alCerrar?.();
  }

  return { abierto: () => contenedor !== null, abrir, cerrar };
}
