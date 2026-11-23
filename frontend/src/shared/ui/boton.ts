import { el } from '../dom';
import { icono, type NombreIcono } from './iconos';

export type VarianteBoton = 'primario' | 'secundario' | 'fantasma' | 'peligro' | 'peligro-secundario';

/** Clases compartidas por botones y enlaces con aspecto de botón (design-system/components.html). */
const BASE =
  'inline-flex items-center justify-center gap-2 min-h-touch px-4 rounded-md border ' +
  'font-sans font-semibold text-base no-underline cursor-pointer select-none ' +
  'transition-[background-color,transform] duration-rapida ease-salida active:scale-presion ' +
  'disabled:bg-superficie-tenue disabled:text-texto-sutil disabled:border-borde disabled:cursor-not-allowed disabled:active:scale-100';

const VARIANTES: Record<VarianteBoton, string> = {
  primario: 'bg-primario text-sobre-primario border-transparent hover:bg-primario-hover active:bg-primario-activo',
  secundario: 'bg-superficie text-primario border-primario hover:bg-primario-tinte',
  fantasma: 'bg-transparent text-texto border-transparent hover:bg-superficie-tenue',
  peligro: 'bg-peligro text-sobre-primario border-transparent hover:opacity-90',
  /** Acción destructiva que no es la principal ("Cancelar turno" en el detalle de C-13). */
  'peligro-secundario': 'bg-superficie text-peligro border-peligro hover:bg-peligro-fondo',
};

export const clasesBoton = (variante: VarianteBoton = 'primario') => `${BASE} ${VARIANTES[variante]}`;

interface OpcionesComunes {
  readonly texto: string;
  readonly variante?: VarianteBoton;
  readonly icono?: NombreIcono;
  /** En mobile muestra solo el ícono (el texto queda para lectores de pantalla). */
  readonly textoSoloDesktop?: boolean;
}

export interface OpcionesBoton extends OpcionesComunes {
  readonly tipo?: 'button' | 'submit';
  readonly alHacerClic?: (evento: MouseEvent) => void;
  /** Acción que modifica datos: se deshabilita sin conexión (spec app-shell, pwa-offline). */
  readonly requiereConexion?: boolean;
}

function contenido({ texto, icono: nombreIcono, textoSoloDesktop = false }: OpcionesComunes): (Node | string)[] {
  const etiqueta = el('span', { class: textoSoloDesktop ? 'max-md:sr-only' : null }, texto);
  return nombreIcono === undefined ? [etiqueta] : [icono(nombreIcono, 'w-icono-sm h-icono-sm flex-none'), etiqueta];
}

export function boton(opciones: OpcionesBoton): HTMLButtonElement {
  const clases = `${clasesBoton(opciones.variante)}${opciones.textoSoloDesktop === true ? ' max-md:w-touch max-md:px-0' : ''}`;
  const b = el(
    'button',
    { type: opciones.tipo ?? 'button', class: clases, 'data-requiere-conexion': opciones.requiereConexion === true },
    ...contenido(opciones),
  );
  if (opciones.alHacerClic !== undefined) b.addEventListener('click', opciones.alHacerClic);
  return b;
}

export interface OpcionesEnlaceBoton extends OpcionesComunes {
  readonly href: string;
}

/** Enlace interno (lo intercepta el router) con aspecto de botón. */
export function enlaceBoton(opciones: OpcionesEnlaceBoton): HTMLAnchorElement {
  return el('a', { href: opciones.href, 'data-link': true, class: clasesBoton(opciones.variante) }, ...contenido(opciones));
}
