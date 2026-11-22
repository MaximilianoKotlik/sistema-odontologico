import fuenteJson from '../contenido/seguridad.json';
import { esFuenteSeguridad, type Afirmacion, type Bloque, type FuenteSeguridad, type Segmento, type Tema } from '../features/seguridad/fuente';
import { el, type Hijo } from '../shared/dom';
import { icono } from '../shared/ui/iconos';

/**
 * Página pública `/seguridad` (C-22, spec pagina-seguridad; mockup aprobado en #40). Se arma al compilar desde la
 * fuente versionada (design D2): no hay pedidos a la API, ni estados de carga o error. El respaldo de cada afirmación
 * vive en la fuente y nunca se pinta.
 */
if (!esFuenteSeguridad(fuenteJson)) throw new Error('src/contenido/seguridad.json no cumple el contrato de la fuente');
const FUENTE: FuenteSeguridad = fuenteJson;

const fechaLarga = new Intl.DateTimeFormat('es-AR', { timeZone: 'UTC', day: 'numeric', month: 'long', year: 'numeric' });
/** "2026-11-22" → "22 de noviembre de 2026", sin pasar por la zona del dispositivo. */
const formatearFecha = (dia: string) => fechaLarga.format(new Date(`${dia}T12:00:00Z`));

const CLASE_TBD = 'inline-block px-1 text-sm font-semibold rounded-sm border border-dashed border-sobreturno-bd bg-sobreturno-bg text-sobreturno-fg';

function segmento(s: Segmento): Hijo {
  if (typeof s === 'string') return s;
  if ('fuerte' in s) return el('strong', {}, s.fuerte);
  if ('tbd' in s) return el('span', { class: CLASE_TBD, 'data-tbd': true }, `(TBD) ${s.tbd}`);
  return el('a', { href: s.href, class: 'text-primario font-semibold underline' }, s.enlace);
}

const contenido = (a: Afirmacion): Hijo[] => a.texto.map(segmento);

function bloque(b: Bloque): HTMLElement {
  switch (b.tipo) {
    case 'parrafo':
      return el('p', { class: `mt-0 mb-3 last:mb-0 ${b.estilo === 'nota' ? 'text-sm text-texto-tenue' : ''}` }, ...contenido(b.afirmacion));
    case 'lista':
      return el('ul', { class: 'mt-0 mb-3 last:mb-0 pl-5 list-disc grid gap-2' }, ...b.items.map((a) => el('li', {}, ...contenido(a))));
    case 'roles':
      return el(
        'table',
        { class: 'w-full border-collapse mb-3 text-base max-[519px]:block' },
        el(
          'thead',
          { class: 'max-[519px]:sr-only' },
          el(
            'tr',
            {},
            el('th', { scope: 'col', class: 'text-left align-top px-2 pb-3 text-sm font-semibold text-texto-tenue' }, 'Quién'),
            el('th', { scope: 'col', class: 'text-left align-top px-2 pb-3 text-sm font-semibold text-texto-tenue' }, 'Qué rol tiene'),
          ),
        ),
        el(
          'tbody',
          { class: 'max-[519px]:block' },
          ...b.filas.map((fila) =>
            el(
              'tr',
              { class: 'border-t border-borde max-[519px]:block max-[519px]:py-3' },
              el('th', { scope: 'row', class: 'w-[38%] text-left align-top px-2 py-3 font-semibold max-[519px]:block max-[519px]:w-auto max-[519px]:p-0' }, fila.quien),
              el('td', { class: 'align-top px-2 py-3 max-[519px]:block max-[519px]:p-0 max-[519px]:text-texto-tenue' }, ...contenido(fila.afirmacion)),
            ),
          ),
        ),
      );
    case 'faltan':
      return el(
        'ul',
        { class: 'm-0 p-0 list-none grid gap-2' },
        ...b.items.map((item) =>
          el(
            'li',
            { class: 'flex gap-3 items-start' },
            icono('alerta', 'w-icono h-icono flex-none mt-1 text-sobreturno-fg'),
            el('span', {}, el('strong', { class: 'block' }, item.titulo), ...contenido(item.afirmacion)),
          ),
        ),
      );
  }
}

function seccion(tema: Tema): HTMLElement {
  const falta = tema.id === 'falta';
  const idTitulo = `${tema.id}-titulo`;
  return el(
    'section',
    {
      id: tema.id,
      'aria-labelledby': idTitulo,
      class: `mb-5 p-5 rounded-lg border shadow-sm scroll-mt-4 ${falta ? 'bg-sobreturno-bg border-sobreturno-bd' : 'bg-superficie border-borde'}`,
    },
    el(
      'h2',
      { id: idTitulo, class: 'flex items-center gap-2 mt-0 mb-3 text-xl font-bold text-texto' },
      icono(tema.icono, `w-6 h-6 flex-none ${falta ? 'text-sobreturno-fg' : 'text-primario'}`),
      tema.titulo,
    ),
    ...tema.bloques.map(bloque),
  );
}

const CLASE_ENLACE_TACTIL = 'inline-flex items-center min-h-touch';

export function montarSeguridad(contenedor: HTMLElement): void {
  const f = FUENTE;
  contenedor.replaceChildren(
    el('h1', { class: 'mt-0 mb-2 text-2xl font-bold text-texto' }, f.titulo),
    el('p', { class: 'mt-0 mb-5 text-lg text-texto-tenue' }, f.bajada),
    el('p', { class: 'mt-0 mb-5 text-sm text-texto-tenue' }, 'Última actualización: ', el('time', { datetime: f.actualizadoEn }, formatearFecha(f.actualizadoEn)), `. ${f.revision}`),
    el(
      'div',
      { role: 'note', 'data-aviso-legal': true, class: 'flex gap-3 items-start mb-5 p-4 rounded-lg border border-sobreturno-bd bg-sobreturno-bg text-sobreturno-fg' },
      icono('alerta', 'w-icono h-icono flex-none mt-1'),
      el('p', { class: 'm-0 [&_a]:text-inherit' }, ...contenido(f.aviso)),
    ),
    el(
      'nav',
      { 'aria-labelledby': 'indice-titulo', class: 'mb-6 px-5 py-4 rounded-lg border border-borde bg-superficie' },
      el('h2', { id: 'indice-titulo', class: 'mt-0 mb-1 text-base font-bold text-texto' }, 'En esta página'),
      el('ol', { class: 'm-0 pl-5 list-decimal' }, ...f.temas.map((t) => el('li', {}, el('a', { href: `#${t.id}`, class: `${CLASE_ENLACE_TACTIL} text-primario underline` }, t.titulo)))),
    ),
    ...f.temas.map(seccion),
    el(
      'nav',
      { 'aria-label': 'Legales', class: 'flex flex-wrap gap-x-4 mt-6 pt-5 border-t border-borde text-sm' },
      ...f.enlacesDelPie.map((e) => el('a', { href: e.href, class: `${CLASE_ENLACE_TACTIL} text-texto-tenue underline` }, e.texto)),
    ),
  );
}
