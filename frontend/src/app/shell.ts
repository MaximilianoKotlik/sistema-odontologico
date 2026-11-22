import type { EstadoConectividad } from '../shared/conectividad';
import { el } from '../shared/dom';
import { formatearHora } from '../shared/fechas';
import type { Store } from '../shared/store';
import { dialogo } from '../shared/ui/dialogo';
import { icono, type NombreIcono } from '../shared/ui/iconos';

/**
 * App-shell privado del odontólogo (spec app-shell, design D8; mockup aprobado de C-06):
 * barra superior con marca, navegación inferior (< 768 px) o lateral (≥ 768 px), "Más",
 * "Saltar al contenido" y aviso sin conexión. Las páginas solo montan su contenido.
 */

export type Seccion = 'agenda' | 'bloqueos' | 'configuracion' | 'auditoria' | 'exportar';

export interface EncabezadoPagina {
  readonly titulo: string;
  /** `cuenta`: páginas del bloque de cuenta (C-07), que no son secciones de la navegación principal. */
  readonly seccion: Seccion | 'cuenta' | null;
}

/** Estado del plan que el shell necesita (C-20): el aviso a ubicar y si las escrituras están bloqueadas. */
export interface PlanDelShell {
  readonly aviso: HTMLElement;
  soloLectura(): boolean;
  suscribir(fn: () => void): () => void;
}

export interface OpcionesShell {
  readonly marca: string;
  readonly conectividad: Store<EstadoConectividad>;
  readonly zonaHoraria: () => string;
  readonly alCerrarSesion: () => void;
  /** Aviso de plan y modo de solo lectura (C-20); sin él, el shell no lo muestra. */
  readonly plan?: PlanDelShell;
}

export interface Shell {
  /** `<main id="contenido">` donde cada página monta su contenido. */
  readonly contenido: HTMLElement;
  /** Lugar de la barra superior para las acciones de la página (p. ej. "Nuevo turno"). */
  readonly acciones: HTMLElement;
  mostrar(pagina: EncabezadoPagina, opciones?: { enfocar?: boolean }): void;
  /** Aplica el estado de conexión a las acciones de escritura presentes. */
  sincronizarConexion(): void;
  destruir(): void;
}

interface Destino {
  readonly seccion: Seccion;
  readonly texto: string;
  readonly icono: NombreIcono;
  readonly iconoRelleno: NombreIcono;
}

const DESTINOS: readonly Destino[] = [
  { seccion: 'agenda', texto: 'Agenda', icono: 'calendario', iconoRelleno: 'calendario-relleno' },
  { seccion: 'bloqueos', texto: 'Bloqueos', icono: 'candado', iconoRelleno: 'candado-relleno' },
  { seccion: 'configuracion', texto: 'Configuración', icono: 'engranaje', iconoRelleno: 'engranaje-relleno' },
  { seccion: 'auditoria', texto: 'Registro de cambios', icono: 'historial', iconoRelleno: 'historial-relleno' },
  { seccion: 'exportar', texto: 'Exportar mis datos', icono: 'descargar', iconoRelleno: 'descargar' },
];
const EN_BARRA_INFERIOR: readonly Seccion[] = ['agenda', 'bloqueos', 'configuracion'];
const EN_HOJA_MAS: readonly Seccion[] = ['auditoria', 'exportar'];

const RUTA_CAMBIAR_CONTRASENA = '/app/cuenta/contrasena';
const ID_MOTIVO_SIN_CONEXION = 'motivo-sin-conexion';
const MOTIVO_SIN_CONEXION = 'Necesitás conexión para crear o cambiar datos.';
/** Lo define el aviso de plan (`features/plan/aviso-de-plan.ts`); el botón deshabilitado apunta a él. */
const ID_AVISO_SOLO_LECTURA = 'aviso-plan-solo-lectura';
const MOTIVO_SOLO_LECTURA = 'Solo lectura: tu prueba terminó.';

/** "Solo lectura" con candado junto a la acción: el estado no se indica solo con color (regla dura 12). */
function motivoSoloLectura(): HTMLSpanElement {
  return el(
    'span',
    { 'data-motivo-solo-lectura': true, 'aria-hidden': 'true', class: 'inline-flex items-center gap-1 text-xs text-texto-tenue' },
    icono('candado', 'w-icono-sm h-icono-sm flex-none'),
    'Solo lectura',
  );
}

const rutaDe = (seccion: Seccion) => `/app/${seccion}`;
const destino = (seccion: Seccion): Destino => {
  const encontrado = DESTINOS.find((d) => d.seccion === seccion);
  if (encontrado === undefined) throw new Error(`Sección desconocida: ${seccion}`);
  return encontrado;
};

/** Ícono de línea y relleno: el relleno se ve solo en el destino activo (no depende solo del color). */
function iconosDestino(d: Destino): SVGSVGElement[] {
  const linea = icono(d.icono, 'w-icono h-icono flex-none group-aria-[current=page]:hidden');
  const relleno = icono(d.iconoRelleno, 'hidden w-icono h-icono flex-none group-aria-[current=page]:block');
  linea.dataset['icono'] = 'linea';
  relleno.dataset['icono'] = 'relleno';
  return [linea, relleno];
}

function marcaVisual(marca: string, clase: string): HTMLAnchorElement {
  return el(
    'a',
    { href: '/app/agenda', 'data-link': true, 'aria-label': `${marca}: ir a mi agenda`, class: `${clase} items-center gap-2 min-w-0 min-h-touch text-texto no-underline` },
    el('span', { class: 'grid place-items-center flex-none w-6 h-6 rounded-md bg-primario text-sobre-primario' }, icono('diente', 'w-icono-sm h-icono-sm')),
    el('span', { 'data-marca': true, class: 'font-bold text-base truncate' }, marca),
  );
}

const CLASE_DESTINO_INFERIOR =
  'group relative flex flex-col items-center justify-center gap-1 w-full min-h-destino-nav py-1 bg-transparent border-0 cursor-pointer ' +
  'font-sans text-xs font-medium text-texto-tenue no-underline ' +
  'aria-[current=page]:text-primario aria-[current=page]:font-bold aria-expanded:text-primario ' +
  "aria-[current=page]:before:content-[''] aria-[current=page]:before:absolute aria-[current=page]:before:top-0 aria-[current=page]:before:inset-x-1/4 " +
  'aria-[current=page]:before:h-indicador aria-[current=page]:before:rounded-b-sm aria-[current=page]:before:bg-primario';
const CLASE_PILDORA =
  'grid place-items-center w-pildora-x h-6 rounded-full transition-colors duration-rapida ease-salida ' +
  'group-aria-[current=page]:bg-primario-tinte group-aria-expanded:bg-primario-tinte';
const CLASE_DESTINO_LATERAL =
  'group relative flex items-center gap-3 w-full min-h-touch px-3 rounded-md bg-transparent border-0 cursor-pointer text-left ' +
  'font-sans text-base font-medium text-texto-tenue no-underline hover:bg-superficie-tenue hover:text-texto ' +
  'aria-[current=page]:bg-primario-tinte aria-[current=page]:text-primario aria-[current=page]:font-bold ' +
  "aria-[current=page]:before:content-[''] aria-[current=page]:before:absolute aria-[current=page]:before:left-0 aria-[current=page]:before:inset-y-3 " +
  'aria-[current=page]:before:w-indicador aria-[current=page]:before:rounded-full aria-[current=page]:before:bg-primario';
const CLASE_ITEM_HOJA =
  'flex items-center gap-3 w-full min-h-touch py-1 px-2 rounded-md bg-transparent border-0 cursor-pointer text-left ' +
  'font-sans text-base font-medium text-texto no-underline hover:bg-superficie-tenue';

export function montarShell(anfitrion: HTMLElement, opciones: OpcionesShell): Shell {
  const { marca, conectividad } = opciones;

  // Barra lateral (≥ 768 px)
  const lateral = el(
    'aside',
    { 'data-nav-lateral': true, class: 'hidden md:flex flex-col flex-none sticky top-0 h-screen w-lateral bg-superficie border-r border-borde px-3 py-4' },
    marcaVisual(marca, 'flex px-2'),
    el('span', { class: 'self-start mx-2 mt-1 mb-5 px-2 rounded-full border text-xs font-semibold text-sobreturno-fg bg-sobreturno-bg border-sobreturno-bd' }, 'Nombre y logo provisorios'),
    el(
      'nav',
      { 'aria-label': 'Secciones' },
      el(
        'ul',
        { class: 'list-none m-0 p-0 grid gap-1' },
        ...DESTINOS.map((d) =>
          el('li', {}, el('a', { href: rutaDe(d.seccion), 'data-link': true, 'data-destino': d.seccion, class: CLASE_DESTINO_LATERAL }, ...iconosDestino(d), d.texto)),
        ),
      ),
    ),
    el('div', { class: 'flex-1' }),
  );
  // Bloque de cuenta (C-07, decisión de Maxi en #14): "Cambiar contraseña" arriba de "Cerrar sesión".
  const contrasenaLateral = el(
    'a',
    { href: RUTA_CAMBIAR_CONTRASENA, 'data-link': true, 'data-destino': 'cuenta', class: CLASE_DESTINO_LATERAL },
    icono('llave'),
    'Cambiar contraseña',
  );
  const salirLateral = el('button', { type: 'button', class: CLASE_DESTINO_LATERAL }, icono('salir'), 'Cerrar sesión');
  salirLateral.addEventListener('click', () => opciones.alCerrarSesion());
  lateral.append(el('div', { 'data-cuenta': true, class: 'grid gap-1 border-t border-borde pt-3 mt-3' }, contrasenaLateral, salirLateral));

  // Barra superior
  const titulo = el('h1', { class: 'm-0 text-lg font-bold md:text-xl max-md:sr-only' });
  const acciones = el('div', { 'data-acciones-pagina': true, class: 'flex items-center gap-2' });
  const barraSuperior = el(
    'header',
    { class: 'sticky top-0 z-30 flex items-center gap-3 px-4 py-2 md:px-6 md:py-3 md:min-h-barra bg-superficie border-b border-borde' },
    marcaVisual(marca, 'flex md:hidden'),
    titulo,
    el('span', { class: 'flex-1' }),
    acciones,
  );

  // Aviso sin conexión
  const textoAviso = el('span');
  const avisoSinConexion = el(
    'div',
    {
      'data-aviso-sin-conexion': true,
      role: 'status',
      hidden: true,
      class: 'flex gap-3 items-start px-4 py-3 md:px-6 bg-bloqueo-bg text-bloqueo-fg border-b border-bloqueo-bd text-sm',
    },
    icono('sin-conexion', 'w-icono-sm h-icono-sm flex-none mt-1'),
    el('div', {}, el('strong', { class: 'text-texto' }, 'Sin conexión.'), textoAviso),
  );

  const contenido = el('main', {
    id: 'contenido',
    tabindex: '-1',
    class: 'flex-1 w-full max-w-5xl mx-auto px-4 pt-4 pb-bajo-nav md:px-6 md:pt-5 md:pb-6 focus:outline-none focus-visible:shadow-none',
  });

  // Navegación inferior (< 768 px) y hoja "Más"
  const botonMas = el(
    'button',
    { type: 'button', 'aria-haspopup': 'dialog', 'aria-expanded': 'false', class: CLASE_DESTINO_INFERIOR },
    el('span', { class: CLASE_PILDORA }, icono('mas-opciones')),
    'Más',
  );
  const navInferior = el(
    'nav',
    {
      'data-nav-inferior': true,
      'aria-label': 'Secciones',
      class: 'md:hidden fixed inset-x-0 bottom-0 z-30 bg-superficie border-t border-borde pb-[env(safe-area-inset-bottom)]',
    },
    el(
      'ul',
      { class: 'list-none m-0 px-1 py-0 grid grid-cols-4' },
      ...EN_BARRA_INFERIOR.map(destino).map((d) =>
        el(
          'li',
          {},
          el('a', { href: rutaDe(d.seccion), 'data-link': true, 'data-destino': d.seccion, class: CLASE_DESTINO_INFERIOR }, el('span', { class: CLASE_PILDORA }, ...iconosDestino(d)), d.texto),
        ),
      ),
      el('li', {}, botonMas),
    ),
  );

  const contrasenaHoja = el(
    'a',
    { href: RUTA_CAMBIAR_CONTRASENA, 'data-link': true, class: CLASE_ITEM_HOJA },
    icono('llave', 'w-icono h-icono flex-none text-texto-tenue'),
    'Cambiar contraseña',
  );
  contrasenaHoja.addEventListener('click', () => hojaMas.cerrar());
  const salirHoja = el('button', { type: 'button', class: CLASE_ITEM_HOJA }, icono('salir', 'w-icono h-icono flex-none text-texto-tenue'), 'Cerrar sesión');
  const listaHoja = el(
    'ul',
    { class: 'list-none m-0 p-0' },
    ...EN_HOJA_MAS.map(destino).map((d) =>
      el('li', {}, el('a', { href: rutaDe(d.seccion), 'data-link': true, class: CLASE_ITEM_HOJA }, icono(d.icono, 'w-icono h-icono flex-none text-texto-tenue'), d.texto)),
    ),
  );
  const hojaMas = dialogo({
    forma: 'hoja',
    titulo: 'Más opciones',
    contenido: el('div', {}, listaHoja, el('hr', { class: 'border-0 border-t border-borde my-2' }), contrasenaHoja, salirHoja),
    acciones: [],
    claseContenedor: 'md:hidden',
    alCerrar: () => botonMas.setAttribute('aria-expanded', 'false'),
  });
  botonMas.addEventListener('click', () => {
    hojaMas.abrir();
    botonMas.setAttribute('aria-expanded', 'true');
  });
  listaHoja.addEventListener('click', (evento) => {
    if (evento.target instanceof Element && evento.target.closest('a') !== null) hojaMas.cerrar();
  });
  salirHoja.addEventListener('click', () => {
    hojaMas.cerrar();
    opciones.alCerrarSesion();
  });

  const saltar = el(
    'a',
    {
      href: '#contenido',
      class: 'sr-only focus:not-sr-only focus:fixed focus:left-2 focus:top-2 focus:z-50 focus:px-3 focus:py-2 bg-superficie text-primario font-semibold rounded-md shadow-md',
    },
    'Saltar al contenido',
  );
  const motivo = el('span', { id: ID_MOTIVO_SIN_CONEXION, class: 'sr-only' }, MOTIVO_SIN_CONEXION);

  anfitrion.replaceChildren(
    saltar,
    el('div', { 'data-app-root': true, class: 'min-h-screen flex flex-col md:flex-row' }, lateral, el('div', { class: 'flex-1 flex flex-col min-w-0' }, barraSuperior, avisoSinConexion, ...(opciones.plan === undefined ? [] : [opciones.plan.aviso]), contenido)),
    navInferior,
    motivo,
  );

  function sincronizarConexion(): void {
    const { online, ultimaSincronizacion } = conectividad.get();
    avisoSinConexion.hidden = online;
    const texto = online
      ? ''
      : `${ultimaSincronizacion === null ? '' : ` Mostrando tu agenda de las ${formatearHora(ultimaSincronizacion, opciones.zonaHoraria())}.`} Para crear o cambiar turnos necesitás conexión.`;
    if (textoAviso.textContent !== texto) textoAviso.textContent = texto;

    const soloLectura = opciones.plan?.soloLectura() === true;
    for (const accion of document.querySelectorAll<HTMLButtonElement>('button[data-requiere-conexion]')) {
      const porPlan = soloLectura && accion.dataset['permitidoEnSoloLectura'] === undefined;
      const nuestra = accion.dataset['deshabilitadoSinConexion'] !== undefined || accion.dataset['deshabilitadoPorPlan'] !== undefined;
      // Una acción que la página deshabilitó por su cuenta no se toca.
      if (!nuestra && accion.disabled) continue;
      if (porPlan || !online) {
        accion.disabled = true;
        if (online) delete accion.dataset['deshabilitadoSinConexion'];
        else accion.dataset['deshabilitadoSinConexion'] = '';
        if (porPlan) accion.dataset['deshabilitadoPorPlan'] = '';
        else delete accion.dataset['deshabilitadoPorPlan'];
        accion.setAttribute('aria-describedby', porPlan ? ID_AVISO_SOLO_LECTURA : ID_MOTIVO_SIN_CONEXION);
        accion.title = porPlan ? MOTIVO_SOLO_LECTURA : MOTIVO_SIN_CONEXION;
        const siguiente = accion.nextElementSibling;
        const tieneMotivo = siguiente?.hasAttribute('data-motivo-solo-lectura') === true;
        if (porPlan && !tieneMotivo) accion.after(motivoSoloLectura());
        if (!porPlan && tieneMotivo) siguiente?.remove();
      } else if (nuestra) {
        accion.disabled = false;
        delete accion.dataset['deshabilitadoSinConexion'];
        delete accion.dataset['deshabilitadoPorPlan'];
        accion.removeAttribute('aria-describedby');
        accion.removeAttribute('title');
        if (accion.nextElementSibling?.hasAttribute('data-motivo-solo-lectura') === true) accion.nextElementSibling.remove();
      }
    }
  }

  const desuscribir = conectividad.suscribir(sincronizarConexion);
  const desuscribirPlan = opciones.plan?.suscribir(sincronizarConexion) ?? (() => undefined);
  // Acciones que una página agrega mientras no hay conexión también quedan deshabilitadas.
  // Solo reacciona a nodos AGREGADOS que traen acciones de escritura: así sus propios cambios
  // (texto del aviso, atributos) no vuelven a dispararlo.
  const traeAcciones = (nodo: Node) =>
    nodo instanceof Element && (nodo.matches('button[data-requiere-conexion]') || nodo.querySelector('button[data-requiere-conexion]') !== null);
  const observador = new MutationObserver((registros) => {
    if (conectividad.get().online && opciones.plan?.soloLectura() !== true) return;
    if (registros.some((r) => [...r.addedNodes].some(traeAcciones))) sincronizarConexion();
  });
  observador.observe(document.body, { childList: true, subtree: true });
  sincronizarConexion();

  return {
    contenido,
    acciones,
    mostrar(pagina, { enfocar = false } = {}) {
      hojaMas.cerrar();
      titulo.textContent = pagina.titulo;
      document.title = `${pagina.titulo} · ${marca}`;
      for (const enlace of anfitrion.querySelectorAll<HTMLAnchorElement>('a[data-destino]')) {
        if (enlace.dataset['destino'] === pagina.seccion) enlace.setAttribute('aria-current', 'page');
        else enlace.removeAttribute('aria-current');
      }
      // En mobile, las secciones de la hoja se ubican por "Más" (mockup de C-19).
      if (EN_HOJA_MAS.some((seccion) => seccion === pagina.seccion)) botonMas.setAttribute('aria-current', 'page');
      else botonMas.removeAttribute('aria-current');
      contenido.replaceChildren();
      acciones.replaceChildren();
      if (enfocar) contenido.focus({ preventScroll: true });
    },
    sincronizarConexion,
    destruir() {
      desuscribir();
      desuscribirPlan();
      observador.disconnect();
      hojaMas.cerrar();
    },
  };
}
