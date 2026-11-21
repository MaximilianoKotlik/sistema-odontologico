import { el, idUnico } from '../dom';
import { icono } from './iconos';

export interface OpcionesCampo {
  readonly etiqueta: string;
  readonly nombre: string;
  readonly tipo?: 'text' | 'email' | 'password' | 'tel' | 'number' | 'date' | 'time';
  readonly valor?: string;
  readonly ayuda?: string;
  readonly error?: string;
  readonly requerido?: boolean;
  readonly autocompletar?: string;
}

/** Campo de formulario con etiqueta visible, ayuda y error asociados (design-system/components.html). */
export function campo(opciones: OpcionesCampo): HTMLDivElement {
  const id = idUnico(`campo-${opciones.nombre}`);
  const idAyuda = opciones.ayuda === undefined ? null : `${id}-ayuda`;
  const idError = opciones.error === undefined ? null : `${id}-error`;
  const describe = [idAyuda, idError].filter((v) => v !== null).join(' ');
  const conError = opciones.error !== undefined;

  const input = el('input', {
    id,
    name: opciones.nombre,
    type: opciones.tipo ?? 'text',
    value: opciones.valor,
    required: opciones.requerido === true,
    autocomplete: opciones.autocompletar,
    'aria-describedby': describe === '' ? null : describe,
    'aria-invalid': conError ? 'true' : null,
    class:
      'min-h-touch w-full px-3 font-sans text-base text-texto bg-superficie border-control border-borde-fuerte rounded-md ' +
      'focus-visible:border-primario aria-[invalid=true]:border-peligro aria-[invalid=true]:bg-peligro-fondo',
  });

  return el(
    'div',
    { class: 'grid gap-1 mb-4' },
    el('label', { for: id, class: 'font-medium text-sm' }, opciones.etiqueta),
    input,
    idAyuda === null ? null : el('p', { id: idAyuda, class: 'm-0 text-sm text-texto-tenue' }, opciones.ayuda),
    idError === null
      ? null
      : el(
          'p',
          { id: idError, class: 'm-0 flex gap-1 items-start text-sm font-medium text-peligro' },
          icono('alerta', 'w-icono-sm h-icono-sm flex-none'),
          el('span', {}, opciones.error),
        ),
  );
}
