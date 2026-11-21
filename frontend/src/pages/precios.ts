import {
  RUTA_PLANES_PUBLICOS,
  esCatalogoPublico,
  formatearActualizacion,
  formatearImporteArs,
  type CatalogoPublico,
  type PlanPublico,
} from '../features/precios/catalogo';
import { el } from '../shared/dom';
import { clasesBoton } from '../shared/ui/boton';
import { errorRecuperable } from '../shared/ui/error-recuperable';

/** Lo único que la página necesita del cliente HTTP: un GET a la API pública (sin token ni cookies). */
export interface FuenteDeCatalogo {
  get<T>(ruta: string): Promise<T>;
}

export interface OpcionesPrecios {
  readonly http: FuenteDeCatalogo;
  readonly marca: string;
}

/** Ruta del registro (C-07). Desde /precios se llega con una navegación completa. */
const RUTA_REGISTRO = '/registro';

function tarjetaPlan(plan: PlanPublico): HTMLElement {
  const titulo = `plan-${plan.id}`;
  return el(
    'section',
    { class: 'bg-superficie border border-borde rounded-lg shadow-sm p-5', 'aria-labelledby': titulo },
    el(
      'div',
      { class: 'flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1' },
      el('h2', { id: titulo, class: 'm-0 text-xl font-bold text-texto' }, plan.nombre),
      el(
        'p',
        { class: 'm-0 text-texto' },
        el('span', { class: 'text-2xl font-bold' }, formatearImporteArs(plan.importeARS)),
        el('span', { class: 'text-texto-tenue' }, ' por mes'),
      ),
    ),
    el('p', { class: 'mt-1 mb-0 text-sm text-texto-tenue' }, 'En pesos argentinos, impuestos incluidos.'),
    plan.descripcion === undefined || plan.descripcion === '' ? null : el('p', { class: 'mt-3 mb-0 text-texto-tenue' }, plan.descripcion),
    el('h3', { class: 'mt-5 mb-2 text-base font-semibold text-texto' }, 'Qué incluye'),
    el(
      'ul',
      { class: 'm-0 pl-5 list-disc grid gap-2 text-texto', 'data-incluye': true },
      ...plan.incluye.map((item) => el('li', {}, item)),
    ),
  );
}

function catalogo(datos: CatalogoPublico): HTMLElement {
  const tituloPrueba = 'prueba-titulo';
  return el(
    'div',
    { class: 'grid gap-5' },
    ...datos.planes.map(tarjetaPlan),
    el(
      'section',
      { class: 'bg-primario-tinte border border-primario rounded-lg p-5', 'aria-labelledby': tituloPrueba },
      el('h2', { id: tituloPrueba, class: 'm-0 text-lg font-bold text-texto' }, `${datos.prueba.dias} días de prueba sin tarjeta ni medio de pago`),
      el(
        'p',
        { class: 'mt-2 mb-0 text-texto' },
        'Probá el sistema completo. No te pedimos tarjeta ni ningún medio de pago, y no hay cobro automático: ' +
          'cuando venza la prueba, el equipo te avisa y activamos el plan a mano.',
      ),
    ),
    el('a', { href: RUTA_REGISTRO, class: `${clasesBoton('primario')} w-full` }, 'Crear mi consultorio'),
    el(
      'p',
      { class: 'm-0 text-sm text-texto-tenue' },
      'Precios en pesos argentinos (ARS), impuestos incluidos. Última actualización: ',
      el('time', { datetime: datos.actualizadoEn }, formatearActualizacion(datos.actualizadoEn)),
      '.',
    ),
  );
}

function cargando(): HTMLElement {
  return el(
    'div',
    { 'aria-busy': 'true', 'aria-live': 'polite', class: 'bg-superficie border border-borde rounded-lg shadow-sm p-5 text-texto-tenue' },
    'Cargando precios…',
  );
}

/**
 * Página pública /precios (US-060, RN-PL-01/02): planes, qué incluye cada uno, importe exacto en ARS, la prueba
 * gratuita sin medio de pago y la fecha de actualización. Si el catálogo no llega o no cumple el contrato muestra un
 * error reintentable y nunca importes inventados. Se resuelve cuando terminó de mostrar el catálogo o el error.
 */
export async function montarPrecios(contenedor: HTMLElement, { http, marca }: OpcionesPrecios): Promise<void> {
  const region = el('div', { 'data-region-precios': true });
  contenedor.replaceChildren(
    el('h1', { class: 'mt-0 mb-2 text-2xl font-bold text-texto' }, `${marca} · Precios claros`),
    el('p', { class: 'mt-0 mb-6 text-lg text-texto-tenue' }, 'Un plan por consultorio. Sin letra chica y sin sorpresas.'),
    region,
  );

  async function cargar(): Promise<void> {
    region.replaceChildren(cargando());
    try {
      const datos: unknown = await http.get<unknown>(RUTA_PLANES_PUBLICOS);
      if (!esCatalogoPublico(datos)) throw new Error('La respuesta no cumple el contrato de planes públicos');
      region.replaceChildren(catalogo(datos));
    } catch {
      region.replaceChildren(
        errorRecuperable({
          titulo: 'No pudimos cargar los precios.',
          texto: 'Puede ser un problema momentáneo. Probá de nuevo en unos segundos.',
          alReintentar: () => void cargar(),
        }),
      );
    }
  }

  await cargar();
}
