import { el } from '../../shared/dom';
import { icono } from '../../shared/ui/iconos';
import { esCatalogoPublico, formatearImporteArs } from '../precios/catalogo';
import { spinner } from './ui';

/**
 * Layout de las pantallas públicas de auth, sin shell (C-07, design D2; mockups `login.html`, `registro.html`):
 * columna única en mobile; desde 900 px, panel de marca a la izquierda (si la pantalla lo pide) y formulario.
 */

const PUNTOS_DEL_PANEL = [
  'Agenda diaria y semanal, sin turnos superpuestos.',
  'Tus pacientes reservan, reprograman o cancelan por link, sin crear cuenta.',
  '30 días de prueba sin tarjeta ni medio de pago.',
] as const;

function marcaVisual(marca: string, enPanel: boolean): HTMLAnchorElement {
  const fondoMarca = enPanel ? 'bg-sobre-primario text-primario' : 'bg-primario text-sobre-primario';
  return el(
    'a',
    {
      href: '/login',
      'data-link': true,
      'aria-label': `${marca}: ir al ingreso`,
      class: `inline-flex items-center gap-2 min-h-touch no-underline ${enPanel ? 'text-sobre-primario' : 'text-texto'}`,
    },
    el('span', { class: `grid place-items-center flex-none w-6 h-6 rounded-md ${fondoMarca}` }, icono('diente', 'w-icono-sm h-icono-sm')),
    el('span', { 'data-marca': true, class: 'font-bold text-base' }, marca),
  );
}

/**
 * Línea de precio del panel, tomada del mismo catálogo público que /precios (C-21; revisión de Maxi en #57). Hasta
 * que llega, o si no cumple el contrato, solo queda "Ver precios": nunca un importe escrito a mano o inventado.
 */
function lineaDePrecio(cargarCatalogo: () => Promise<unknown>): HTMLParagraphElement {
  const linea = el(
    'p',
    { 'data-precio-panel': true, class: 'm-0 text-sm opacity-90' },
    el('a', { href: '/precios', class: 'text-current underline underline-offset-2' }, 'Ver precios'),
  );
  cargarCatalogo()
    .then((datos) => {
      if (!esCatalogoPublico(datos)) return;
      const [plan, ...otros] = datos.planes;
      if (plan === undefined) return;
      const importe = formatearImporteArs(plan.importeARS);
      const texto = otros.length === 0 ? `Plan único: ${importe} por mes con IVA incluido. ` : `Desde ${importe} por mes con IVA incluido. `;
      linea.prepend(texto);
    })
    .catch(() => undefined);
  return linea;
}

function panel(marca: string, cargarCatalogo: () => Promise<unknown>): HTMLElement {
  return el(
    'aside',
    {
      'aria-label': 'Sobre el producto',
      class: 'hidden panel:flex flex-col justify-between basis-5/12 max-w-xl flex-none bg-primario text-sobre-primario px-6 py-7',
    },
    marcaVisual(marca, true),
    el(
      'div',
      {},
      el('h2', { class: 'mt-0 mb-5 text-2xl max-w-xs' }, 'Tu agenda del consultorio, ordenada.'),
      el(
        'ul',
        { class: 'list-none m-0 p-0 grid gap-4' },
        ...PUNTOS_DEL_PANEL.map((texto) => el('li', { class: 'flex gap-3 items-start text-lg' }, icono('check', 'w-icono h-icono flex-none mt-1'), texto)),
      ),
    ),
    lineaDePrecio(cargarCatalogo),
  );
}

export interface LayoutAuth {
  /** Tarjeta donde cada pantalla monta su contenido. */
  readonly tarjeta: HTMLElement;
}

/** `cargarCatalogo` trae `GET /api/v1/public/planes`; solo se llama si la pantalla muestra el panel. */
export function montarLayoutAuth(anfitrion: HTMLElement, marca: string, conPanel: boolean, cargarCatalogo: () => Promise<unknown>): LayoutAuth {
  const tarjeta = el('div', { class: 'w-full max-w-md' });
  const principal = el(
    'main',
    {
      id: 'contenido',
      tabindex: '-1',
      class: `flex-1 flex flex-col items-center px-4 pt-4 pb-6 panel:px-6 panel:py-7 focus:outline-none${conPanel ? ' panel:justify-center' : ''}`,
    },
    el(
      'div',
      { class: 'w-full max-w-md flex items-center justify-between gap-2 flex-wrap mb-5' },
      el('span', { class: conPanel ? 'panel:invisible' : '' }, marcaVisual(marca, false)),
      el(
        'span',
        { class: 'inline-block px-2 rounded-full border text-xs font-semibold text-sobreturno-fg bg-sobreturno-bg border-sobreturno-bd' },
        'Nombre y logo provisorios',
      ),
    ),
    tarjeta,
  );
  const saltar = el(
    'a',
    {
      href: '#contenido',
      class: 'sr-only focus:not-sr-only focus:fixed focus:left-2 focus:top-2 focus:z-50 focus:px-3 focus:py-2 bg-superficie text-primario font-semibold rounded-md shadow-md',
    },
    'Saltar al formulario',
  );
  anfitrion.replaceChildren(
    saltar,
    el('div', { 'data-auth-root': true, class: 'min-h-screen flex flex-col panel:flex-row bg-fondo font-sans' }, conPanel ? panel(marca, cargarCatalogo) : null, principal),
  );
  return { tarjeta };
}

/** Splash de la restauración de sesión al recargar (D3): un instante antes de la agenda o del login. */
export function montarSplash(anfitrion: HTMLElement): void {
  anfitrion.replaceChildren(
    el(
      'div',
      { role: 'status', 'aria-live': 'polite', 'aria-busy': 'true', class: 'min-h-screen grid place-items-center content-center gap-4 text-center text-texto-tenue bg-fondo' },
      el('h1', { class: 'sr-only' }, 'Abriendo tu agenda'),
      el('span', { 'aria-hidden': 'true', class: 'grid place-items-center w-7 h-7 rounded-md bg-primario text-sobre-primario' }, icono('diente')),
      el('p', { class: 'm-0 inline-flex items-center gap-2' }, spinner(), 'Abriendo tu agenda…'),
    ),
  );
}
