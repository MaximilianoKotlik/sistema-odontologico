import { el, idUnico, type Hijo } from '../../shared/dom';
import { boton } from '../../shared/ui/boton';
import { icono, type NombreIcono } from '../../shared/ui/iconos';
import { avisoAuth, spinner } from '../auth/ui';

/** Piezas de las pantallas de Configuración (C-09; mockups `config.css`), traducidas a los tokens vía Tailwind. */

export type Pestana = 'consultorio' | 'prestaciones' | 'horarios';

const PESTANAS: readonly { readonly id: Pestana; readonly texto: string; readonly ruta: string; readonly icono: NombreIcono }[] = [
  { id: 'consultorio', texto: 'Consultorio', ruta: '/app/configuracion', icono: 'diente' },
  { id: 'prestaciones', texto: 'Prestaciones', ruta: '/app/configuracion/prestaciones', icono: 'lista' },
  { id: 'horarios', texto: 'Horarios', ruta: '/app/configuracion/horarios', icono: 'reloj' },
];

const CLASE_PESTANA =
  'relative flex-[1_0_auto] md:flex-none inline-flex items-center justify-center gap-2 min-h-touch px-2 md:px-3 no-underline whitespace-nowrap ' +
  'font-medium text-texto-tenue hover:text-texto aria-[current=page]:text-primario aria-[current=page]:font-bold ' +
  "aria-[current=page]:after:content-[''] aria-[current=page]:after:absolute aria-[current=page]:after:inset-x-2 aria-[current=page]:after:-bottom-px " +
  'aria-[current=page]:after:h-indicador aria-[current=page]:after:rounded-t-full aria-[current=page]:after:bg-primario';

/** Pestañas como links con `aria-current` (D2): cada una es una URL navegable con historial propio. */
export function pestanas(actual: Pestana): HTMLElement {
  return el(
    'nav',
    { 'aria-label': 'Secciones de configuración', class: 'flex overflow-x-auto -mx-4 md:mx-0 mb-5 px-2 md:px-0 border-b border-borde' },
    ...PESTANAS.map((p) =>
      el(
        'a',
        { href: p.ruta, 'data-link': true, 'aria-current': p.id === actual ? 'page' : null, class: CLASE_PESTANA },
        icono(p.icono, 'max-[419px]:hidden w-icono-sm h-icono-sm flex-none'),
        p.texto,
      ),
    ),
  );
}

export function tarjeta(titulo: string, subtitulo: string | null, ...hijos: Hijo[]): HTMLElement {
  const id = idUnico('tarjeta');
  return el(
    'section',
    { 'aria-labelledby': id, class: 'min-w-0 bg-superficie border border-borde rounded-lg p-4 md:p-5' },
    el(
      'div',
      { class: 'mb-4' },
      el('h2', { id, class: 'm-0 text-lg font-bold' }, titulo),
      subtitulo === null ? null : el('p', { class: 'mt-1 mb-0 text-sm text-texto-tenue' }, subtitulo),
    ),
    ...hijos,
  );
}

export const CLASE_CONTROL =
  'min-h-touch w-full min-w-0 px-3 font-sans text-base text-texto bg-superficie border-control border-borde-fuerte rounded-md ' +
  'focus-visible:border-primario aria-[invalid=true]:border-peligro aria-[invalid=true]:bg-peligro-fondo';

export interface CampoConfig<C extends HTMLInputElement | HTMLSelectElement> {
  readonly contenedor: HTMLDivElement;
  readonly control: C;
  error(contenido: string | readonly Hijo[] | null): void;
  /** Contenedor debajo del control para avisos o chips propios del campo. */
  readonly extra: HTMLDivElement;
}

export interface OpcionesCampoConfig<C extends HTMLInputElement | HTMLSelectElement> {
  readonly etiqueta: string;
  readonly control: C;
  readonly ayuda?: string;
  readonly opcional?: boolean;
  readonly prefijo?: string;
  readonly sufijo?: string;
  /** Ancho acotado para números cortos. */
  readonly corto?: boolean;
}

/** Campo con etiqueta, ayuda (que se oculta con un error, como en el mockup), error y prefijo o sufijo. */
export function campoConfig<C extends HTMLInputElement | HTMLSelectElement>(opciones: OpcionesCampoConfig<C>): CampoConfig<C> {
  const { control } = opciones;
  control.id ||= idUnico(`config-${control.getAttribute('name') ?? 'campo'}`);
  const idAyuda = `${control.id}-ayuda`;
  const idError = `${control.id}-error`;
  const ayuda = opciones.ayuda === undefined ? null : el('p', { id: idAyuda, class: 'm-0 text-sm text-texto-tenue' }, opciones.ayuda);
  const mensaje = el('p', { id: idError, hidden: true, class: 'm-0 flex gap-1 items-start text-sm font-medium text-peligro' });
  const extra = el('div', {});

  const conAfijo = opciones.prefijo !== undefined || opciones.sufijo !== undefined;
  let cuerpo: HTMLElement = control;
  if (conAfijo) {
    control.className = `${CLASE_CONTROL} border-0 rounded-none focus-visible:outline-none`;
    const afijo = (texto: string, lado: 'izq' | 'der') =>
      el('span', { 'aria-hidden': lado === 'izq' ? 'true' : null, class: `flex items-center px-3 bg-superficie-tenue text-texto-tenue text-sm whitespace-nowrap ${lado === 'izq' ? 'border-r' : 'border-l'} border-borde` }, texto);
    cuerpo = el(
      'div',
      {
        class:
          `flex items-stretch overflow-hidden rounded-md border-control border-borde-fuerte bg-superficie focus-within:outline focus-within:outline-2 focus-within:outline-offset-2 focus-within:outline-primario ` +
          `has-[[aria-invalid=true]]:border-peligro ${opciones.corto === true ? 'max-w-xs' : ''}`,
      },
      opciones.prefijo === undefined ? null : afijo(opciones.prefijo, 'izq'),
      control,
      opciones.sufijo === undefined ? null : afijo(opciones.sufijo, 'der'),
    );
  } else {
    control.className = `${CLASE_CONTROL}${opciones.corto === true ? ' max-w-xs' : ''}`;
  }

  const describir = (conError: boolean) => {
    const ids = [conError || ayuda === null ? null : idAyuda, conError ? idError : null].filter((v) => v !== null);
    if (ids.length === 0) control.removeAttribute('aria-describedby');
    else control.setAttribute('aria-describedby', ids.join(' '));
  };
  describir(false);

  const contenedor = el(
    'div',
    { class: 'grid gap-1 mb-4' },
    el('label', { for: control.id, class: 'font-medium text-sm' }, opciones.etiqueta, opciones.opcional === true ? el('span', { class: 'font-normal text-texto-tenue' }, ' (opcional)') : null),
    cuerpo,
    ayuda,
    mensaje,
    extra,
  );

  return {
    contenedor,
    control,
    extra,
    error(contenido) {
      if (contenido === null) {
        mensaje.hidden = true;
        mensaje.replaceChildren();
        control.removeAttribute('aria-invalid');
        if (ayuda !== null) ayuda.hidden = false;
        describir(false);
        return;
      }
      mensaje.replaceChildren(icono('alerta', 'w-icono-sm h-icono-sm flex-none mt-1'), el('span', {}, ...(typeof contenido === 'string' ? [contenido] : contenido)));
      mensaje.hidden = false;
      control.setAttribute('aria-invalid', 'true');
      if (ayuda !== null) ayuda.hidden = true;
      describir(true);
    },
  };
}

/** Región `aria-live="polite"` para confirmaciones ("Guardamos los cambios", "¡Copiado!"), D10. */
export function regionAnuncios(): { readonly elemento: HTMLElement; anunciar(texto: string): void } {
  const elemento = el('p', { 'aria-live': 'polite', class: 'sr-only' });
  return {
    elemento,
    anunciar(texto) {
      elemento.textContent = '';
      // Se vacía y se vuelve a escribir para que el lector anuncie aunque el texto se repita.
      queueMicrotask(() => {
        elemento.textContent = texto;
      });
      elemento.textContent = texto;
    },
  };
}

export interface BarraDeGuardado {
  readonly elemento: HTMLElement;
  readonly botonGuardar: HTMLButtonElement;
  mostrar(visible: boolean): void;
  guardando(activo: boolean): void;
}

/** Barra fija "Tenés cambios sin guardar" con "Descartar" y el botón de guardar (D7, mockups decisión 7). */
export function barraDeGuardado(textoGuardar: string, alDescartar: () => void): BarraDeGuardado {
  const descartar = boton({ texto: 'Descartar', variante: 'fantasma', alHacerClic: alDescartar });
  const botonGuardar = boton({ texto: textoGuardar, tipo: 'submit', requiereConexion: true });
  const elemento = el(
    'div',
    {
      'data-barra-guardado': true,
      hidden: true,
      class:
        'fixed inset-x-0 bottom-destino-nav md:bottom-0 md:left-lateral z-20 flex flex-wrap items-center justify-between gap-x-3 gap-y-2 ' +
        'px-4 py-2 bg-superficie border-t border-borde shadow-md',
    },
    el('p', { class: 'm-0 flex items-center gap-2 text-sm font-semibold' }, icono('lapiz', 'w-icono-sm h-icono-sm flex-none text-sobreturno-fg'), 'Tenés cambios sin guardar'),
    el('div', { class: 'flex gap-2 ml-auto' }, descartar, botonGuardar),
  );
  return {
    elemento,
    botonGuardar,
    mostrar(visible) {
      elemento.hidden = !visible;
    },
    guardando(activo) {
      descartar.hidden = activo;
      botonGuardar.disabled = activo;
      botonGuardar.replaceChildren(...(activo ? [spinner(), 'Guardando…'] : [textoGuardar]));
    },
  };
}

/** Resumen de errores enfocable con un link a cada campo (patrón del registro de C-07, D10). */
export function resumenDeErrores(titulo: string, errores: readonly { readonly id: string; readonly mensaje: string }[]): HTMLDivElement {
  const resumen = avisoAuth({ tono: 'peligro', icono: 'alerta', titulo });
  resumen.setAttribute('tabindex', '-1');
  resumen.dataset['resumenErrores'] = '';
  resumen.lastElementChild?.append(
    el('ul', { class: 'mt-1 mb-0 pl-5 list-disc' }, ...errores.map((e) => el('li', {}, el('a', { href: `#${e.id}`, class: 'font-semibold underline text-current' }, e.mensaje)))),
  );
  return resumen;
}

export function cantidadDeDatos(cantidad: number): string {
  return cantidad === 1 ? 'Revisá 1 dato antes de guardar' : `Revisá ${cantidad} datos antes de guardar`;
}
