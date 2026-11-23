import { montarPrecios } from './pages/precios';
import { el } from './shared/dom';
import { crearClienteHttp, type ClienteHttp } from './shared/http';
import { nombreDeMarca } from './shared/marca';
import { compilarPatron, type Parametros } from './shared/router';
import { icono } from './shared/ui/iconos';

export interface DependenciasPublicas {
  readonly fetch?: typeof fetch;
}

/** Lo que recibe cada página pública al montarse. */
export interface ContextoPublico {
  readonly http: ClienteHttp;
  readonly marca: string;
  readonly params: Parametros;
  /**
   * Cabecera de la página. En el layout "consultorio" empieza vacía y oculta: la página la llena con el nombre,
   * la dirección y el teléfono del consultorio (DD-33) cuando los tiene, y la deja oculta ante un link inválido.
   */
  readonly cabecera: HTMLElement;
}

interface PaginaPublica {
  readonly patron: string;
  readonly titulo: string;
  /** "marca": cabecera con la marca (/precios). "consultorio": la página es del consultorio y la marca va al pie (C-16). */
  readonly layout: 'marca' | 'consultorio';
  montar(contenedor: HTMLElement, contexto: ContextoPublico): Promise<void>;
}

/**
 * Páginas para visitantes sin cuenta (C-21, C-16). No usan el shell del odontólogo: no hay sesión, navegación ni
 * Authorization. El cliente HTTP solo manda token a rutas privadas, así que acá nunca viaja una credencial.
 * Cada página se carga con import() para que el bundle público no arrastre las otras.
 */
const PAGINAS: readonly PaginaPublica[] = [
  { patron: '/precios', titulo: 'Precios', layout: 'marca', montar: (contenedor, contexto) => montarPrecios(contenedor, contexto) },
  // C-22, design D2: la página se arma al compilar desde la fuente versionada, sin pedidos a la API.
  { patron: '/seguridad', titulo: 'Seguridad y datos', layout: 'marca', montar: async (contenedor) => (await import('./pages/seguridad')).montarSeguridad(contenedor) },
  {
    patron: '/c/:slug',
    titulo: 'Reservá tu turno',
    layout: 'consultorio',
    montar: async (contenedor, contexto) => (await import('./pages/reserva-publica')).montarReservaPublica(contenedor, contexto),
  },
  {
    patron: '/t/:token',
    titulo: 'Tu turno',
    layout: 'consultorio',
    montar: async (contenedor, contexto) => (await import('./pages/gestion-turno')).montarGestionTurno(contenedor, contexto),
  },
];

const COMPILADAS = PAGINAS.map((pagina) => ({ pagina, ...compilarPatron(pagina.patron) }));

/** Un `%` mal formado no rompe la página: se deja el valor crudo y la API decide (un token así responde 404). */
function decodificar(valor: string): string {
  try {
    return decodeURIComponent(valor);
  } catch {
    return valor;
  }
}

function resolver(ruta: string): { pagina: PaginaPublica; params: Parametros } | null {
  for (const { pagina, expresion, nombres } of COMPILADAS) {
    const coincidencia = expresion.exec(ruta);
    if (coincidencia === null) continue;
    return { pagina, params: Object.fromEntries(nombres.map((nombre, i) => [nombre, decodificar(coincidencia[i + 1] ?? '')])) };
  }
  return null;
}

export function esRutaPublica(ruta: string): boolean {
  return resolver(ruta) !== null;
}

function logoDeMarca(marca: string): HTMLElement {
  return el(
    'span',
    { class: 'inline-flex items-center gap-2 text-texto' },
    el('span', { class: 'grid place-items-center flex-none w-6 h-6 rounded-md bg-primario text-sobre-primario' }, icono('diente', 'w-icono-sm h-icono-sm')),
    el('span', { 'data-marca': true, class: 'font-bold text-base' }, marca),
  );
}

function layoutDeMarca(marca: string): { raiz: HTMLElement; cabecera: HTMLElement; contenido: HTMLElement } {
  const cabecera = el('header', { class: 'mx-auto w-full max-w-2xl px-4 py-5' }, logoDeMarca(marca));
  const contenido = el('main', { id: 'contenido', class: 'mx-auto w-full max-w-2xl px-4 pb-7' });
  return { raiz: el('div', { class: 'min-h-screen bg-fondo font-sans' }, cabecera, contenido), cabecera, contenido };
}

/** Layout de /c y /t: la página es del consultorio (DD-33); la marca aparece solo en el pie (mockup de C-16). */
function layoutDeConsultorio(marca: string): { raiz: HTMLElement; cabecera: HTMLElement; contenido: HTMLElement } {
  const cabecera = el('header', { 'data-cabecera-consultorio': true, hidden: true, class: 'bg-superficie border-b border-borde' });
  const contenido = el('main', { id: 'contenido', tabindex: '-1', class: 'flex-1 mx-auto w-full max-w-xl px-4 pt-5 pb-6 md:pt-6 focus:outline-none' });
  const raiz = el(
    'div',
    { class: 'min-h-screen flex flex-col bg-fondo font-sans text-texto' },
    el(
      'a',
      {
        href: '#contenido',
        class:
          'sr-only focus:not-sr-only focus:absolute focus:left-2 focus:top-2 focus:z-50 focus:bg-superficie focus:text-primario focus:px-3 focus:py-2 focus:rounded-md focus:shadow-md font-semibold',
      },
      'Saltar al contenido',
    ),
    cabecera,
    contenido,
    el(
      'footer',
      { class: 'border-t border-borde pt-4 pb-5 text-sm text-texto-tenue' },
      el(
        'div',
        { class: 'mx-auto w-full max-w-xl px-4 flex flex-wrap items-center justify-between gap-x-4 gap-y-1' },
        el('span', {}, 'Turnos online con ', el('span', { 'data-marca': true }, marca)),
        el('a', { href: '/privacidad', class: 'inline-flex items-center min-h-touch text-texto-tenue' }, 'Política de privacidad'),
      ),
    ),
  );
  return { raiz, cabecera, contenido };
}

/** Monta una página pública en #app, sin el shell del odontólogo. */
export async function montarPaginaPublica(documento: Document, ruta: string, dependencias: DependenciasPublicas = {}): Promise<void> {
  const anfitrion = documento.querySelector<HTMLElement>('#app');
  if (anfitrion === null) throw new Error('No se encontró el elemento #app');
  const resuelta = resolver(ruta);
  if (resuelta === null) throw new Error(`No hay una página pública en ${ruta}`);
  const { pagina, params } = resuelta;

  const marca = nombreDeMarca();
  documento.title = `${pagina.titulo} · ${marca}`;
  const { raiz, cabecera, contenido } = pagina.layout === 'marca' ? layoutDeMarca(marca) : layoutDeConsultorio(marca);
  anfitrion.replaceChildren(raiz);

  const http = crearClienteHttp(dependencias.fetch === undefined ? {} : { fetch: dependencias.fetch });
  await pagina.montar(contenido, { http, marca, params, cabecera });
}
