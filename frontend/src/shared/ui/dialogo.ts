import { el, enfocables, idUnico } from '../dom';
import { icono } from './iconos';

export interface OpcionesDialogo {
  readonly titulo: string;
  readonly descripcion?: string;
  readonly contenido?: Node;
  readonly acciones: readonly HTMLElement[];
  readonly alCerrar?: () => void;
  /**
   * Presentación: centrado (por defecto), hoja inferior (menú "Más" en mobile) o formulario (C-14, design D2): hoja a
   * pantalla completa en mobile con el botón principal fijo abajo y diálogo de hasta 560 px en desktop.
   */
  readonly forma?: 'centrado' | 'hoja' | 'formulario';
  /** Clases extra del contenedor (p. ej. ocultarlo desde md). */
  readonly claseContenedor?: string;
  /** `alertdialog` para confirmaciones de acciones terminales (C-14). */
  readonly rol?: 'dialog' | 'alertdialog';
  /** Dónde arranca el foco; por defecto, el primer control del contenido (o de la caja). */
  readonly focoInicial?: HTMLElement;
  /**
   * Se consulta antes de cerrar por Escape, velo o "Cerrar" (p. ej. para confirmar el descarte de cambios, D2).
   * `cerrar()` desde el código no pregunta.
   */
  readonly antesDeCerrar?: () => Promise<boolean>;
}

export interface Dialogo {
  readonly abierto: () => boolean;
  abrir(): void;
  cerrar(): void;
}

const CLASES_CAJA = {
  centrado:
    'fixed left-1/2 top-1/2 z-50 w-[calc(100%-var(--space-6))] max-w-md -translate-x-1/2 -translate-y-1/2 bg-superficie border border-borde rounded-lg shadow-lg p-5',
  hoja: 'fixed inset-x-0 bottom-0 z-50 bg-superficie rounded-t-lg shadow-lg px-4 pt-2 pb-4 motion-safe:animate-subir',
  formulario:
    'fixed inset-0 z-50 flex flex-col bg-superficie shadow-lg ' +
    'md:inset-auto md:left-1/2 md:top-1/2 md:-translate-x-1/2 md:-translate-y-1/2 md:w-[calc(100%-var(--space-7))] md:max-w-xl ' +
    'md:max-h-[calc(100vh-var(--space-7)-var(--space-4))] md:rounded-lg md:border md:border-borde md:overflow-hidden',
} as const;

/**
 * Diálogo modal accesible (design D4): role="dialog", aria-modal, título asociado,
 * foco atrapado, Escape y velo cierran, y el foco vuelve a quien lo abrió.
 * Se usa un div con roles ARIA porque <dialog>.showModal() no está disponible en jsdom.
 */
export function dialogo(opciones: OpcionesDialogo): Dialogo {
  const idTitulo = idUnico('dialogo-titulo');
  const idDescripcion = opciones.descripcion === undefined ? null : idUnico('dialogo-desc');
  const forma = opciones.forma ?? 'centrado';
  let contenedor: HTMLDivElement | null = null;
  let previo: HTMLElement | null = null;
  let consultando = false;

  /** Cierre pedido por la persona: pasa por `antesDeCerrar` si hay. */
  async function pedirCierre(): Promise<void> {
    if (opciones.antesDeCerrar === undefined) return cerrar();
    if (consultando) return;
    consultando = true;
    try {
      if (await opciones.antesDeCerrar()) cerrar();
    } finally {
      consultando = false;
    }
  }

  function alTeclear(evento: KeyboardEvent): void {
    if (contenedor === null) return;
    if (evento.key === 'Escape') {
      evento.preventDefault();
      void pedirCierre();
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

  function cajaFormulario(): HTMLElement {
    const cerrarX = el(
      'button',
      { type: 'button', 'aria-label': 'Cerrar', class: 'inline-flex items-center justify-center min-h-touch min-w-touch -mr-2 rounded-md text-texto hover:bg-superficie-tenue' },
      icono('cruz', 'w-icono h-icono'),
    );
    cerrarX.addEventListener('click', () => void pedirCierre());
    return el(
      'div',
      { 'data-forma': 'formulario', role: opciones.rol ?? 'dialog', 'aria-modal': 'true', 'aria-labelledby': idTitulo, 'aria-describedby': idDescripcion, tabindex: '-1', class: CLASES_CAJA.formulario },
      el(
        'div',
        { class: 'flex items-center justify-between gap-2 px-4 py-3 border-b border-borde bg-superficie' },
        el('h2', { id: idTitulo, class: 'm-0 text-lg font-bold' }, opciones.titulo),
        cerrarX,
      ),
      el(
        'div',
        { 'data-cuerpo': true, class: 'flex-1 overflow-auto p-4' },
        idDescripcion === null ? null : el('p', { id: idDescripcion, class: 'm-0 mb-4 text-texto-tenue' }, opciones.descripcion),
        opciones.contenido ?? null,
      ),
      opciones.acciones.length === 0
        ? null
        : el('div', { 'data-pie': true, class: 'flex gap-2 px-4 pt-3 pb-[calc(var(--space-3)+env(safe-area-inset-bottom))] border-t border-borde bg-superficie md:justify-end' }, ...opciones.acciones),
    );
  }

  function cajaSimple(): HTMLElement {
    const esHoja = forma === 'hoja';
    return el(
      'div',
      { role: opciones.rol ?? 'dialog', 'aria-modal': 'true', 'aria-labelledby': idTitulo, 'aria-describedby': idDescripcion, tabindex: '-1', class: CLASES_CAJA[esHoja ? 'hoja' : 'centrado'] },
      esHoja ? el('div', { 'aria-hidden': 'true', class: 'w-6 h-1 rounded-full bg-borde-fuerte mx-auto mt-1 mb-3' }) : null,
      el('h2', { id: idTitulo, class: esHoja ? 'm-0 mb-2 text-base font-semibold text-texto-tenue' : 'm-0 mb-2 text-lg font-bold' }, opciones.titulo),
      idDescripcion === null ? null : el('p', { id: idDescripcion, class: 'm-0 mb-4 text-texto-tenue' }, opciones.descripcion),
      opciones.contenido ?? null,
      opciones.acciones.length === 0 ? null : el('div', { class: 'flex flex-wrap gap-2 justify-end' }, ...opciones.acciones),
    );
  }

  function abrir(): void {
    if (contenedor !== null) return;
    previo = document.activeElement instanceof HTMLElement ? document.activeElement : null;

    const caja = forma === 'formulario' ? cajaFormulario() : cajaSimple();
    const velo = el('div', { 'data-velo': true, 'aria-hidden': 'true', class: 'fixed inset-0 z-40 bg-velo' });
    velo.addEventListener('click', () => void pedirCierre());

    contenedor = el('div', { class: opciones.claseContenedor ?? null }, velo, caja);
    contenedor.addEventListener('keydown', alTeclear);
    document.body.append(contenedor);
    const cuerpo = caja.querySelector<HTMLElement>('[data-cuerpo]');
    (opciones.focoInicial ?? (cuerpo === null ? undefined : enfocables(cuerpo)[0]) ?? enfocables(caja)[0] ?? caja).focus();
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
