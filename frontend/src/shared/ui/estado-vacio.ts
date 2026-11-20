import { el } from '../dom';
import { icono, type NombreIcono } from './iconos';

export interface OpcionesEstado {
  readonly icono: NombreIcono;
  readonly titulo: string;
  readonly texto?: string;
  readonly acciones?: readonly HTMLElement[];
}

/** Tarjeta de estado centrada (vacío, sin conexión, no encontrada) del mockup de C-06. */
export function tarjetaEstado(opciones: OpcionesEstado, extra: { clase?: string; role?: string } = {}): HTMLElement {
  return el(
    'section',
    {
      role: extra.role,
      class: `bg-superficie border border-borde rounded-lg shadow-sm text-center px-4 py-6 ${extra.clase ?? 'text-texto-sutil'}`,
    },
    icono(opciones.icono, 'w-7 h-7 mx-auto'),
    el('h2', { class: 'mt-3 mb-1 text-lg font-bold text-texto' }, opciones.titulo),
    opciones.texto === undefined ? null : el('p', { class: 'mx-auto mt-0 mb-4 max-w-lectura text-texto-tenue' }, opciones.texto),
    (opciones.acciones ?? []).length === 0 ? null : el('div', { class: 'flex flex-wrap gap-2 justify-center' }, ...(opciones.acciones ?? [])),
  );
}

export function estadoVacio(opciones: OpcionesEstado): HTMLElement {
  return tarjetaEstado(opciones);
}
