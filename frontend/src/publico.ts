import { montarPrecios } from './pages/precios';
import { el } from './shared/dom';
import { crearClienteHttp } from './shared/http';
import { nombreDeMarca } from './shared/marca';
import { icono } from './shared/ui/iconos';

export interface DependenciasPublicas {
  readonly fetch?: typeof fetch;
}

interface PaginaPublica {
  readonly titulo: string;
  montar(contenedor: HTMLElement, contexto: { http: ReturnType<typeof crearClienteHttp>; marca: string }): Promise<void>;
}

/**
 * Páginas para visitantes sin cuenta (C-21). No usan el shell del odontólogo: no hay sesión, navegación ni
 * Authorization. El cliente HTTP solo manda token a rutas privadas, así que acá nunca viaja una credencial.
 */
const PAGINAS: Readonly<Record<string, PaginaPublica>> = {
  '/precios': { titulo: 'Precios', montar: (contenedor, contexto) => montarPrecios(contenedor, contexto) },
};

const normalizar = (ruta: string) => (ruta.length > 1 && ruta.endsWith('/') ? ruta.slice(0, -1) : ruta);

export function esRutaPublica(ruta: string): boolean {
  return Object.hasOwn(PAGINAS, normalizar(ruta));
}

/** Monta una página pública en #app con una cabecera mínima de marca. */
export async function montarPaginaPublica(documento: Document, ruta: string, dependencias: DependenciasPublicas = {}): Promise<void> {
  const anfitrion = documento.querySelector<HTMLElement>('#app');
  if (anfitrion === null) throw new Error('No se encontró el elemento #app');
  const pagina = PAGINAS[normalizar(ruta)];
  if (pagina === undefined) throw new Error(`No hay una página pública en ${ruta}`);

  const marca = nombreDeMarca();
  documento.title = `${pagina.titulo} · ${marca}`;
  const contenido = el('main', { id: 'contenido', class: 'mx-auto w-full max-w-2xl px-4 pb-7' });
  anfitrion.replaceChildren(
    el(
      'div',
      { class: 'min-h-screen bg-fondo font-sans' },
      el(
        'header',
        { class: 'mx-auto w-full max-w-2xl px-4 py-5' },
        el(
          'span',
          { class: 'inline-flex items-center gap-2 text-texto' },
          el('span', { class: 'grid place-items-center flex-none w-6 h-6 rounded-md bg-primario text-sobre-primario' }, icono('diente', 'w-icono-sm h-icono-sm')),
          el('span', { 'data-marca': true, class: 'font-bold text-base' }, marca),
        ),
      ),
      contenido,
    ),
  );

  const http = crearClienteHttp(dependencias.fetch === undefined ? {} : { fetch: dependencias.fetch });
  await pagina.montar(contenido, { http, marca });
}
