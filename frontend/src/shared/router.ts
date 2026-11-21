/**
 * Router propio por History API con tabla declarativa (spec app-shell, design D1).
 * `cargar` puede devolver una promesa (p. ej. `import()`) para dividir el bundle por ruta.
 */

export type Parametros = Readonly<Record<string, string>>;

export interface DefinicionRuta<P> {
  readonly patron: string;
  readonly cargar: (params: Parametros) => P | Promise<P>;
}

export interface Resolucion<P> {
  /** Ruta completa (path + query) que se resolvió. */
  readonly ruta: string;
  /** Patrón que coincidió, o null si es la página no encontrada. */
  readonly patron: string | null;
  readonly params: Parametros;
  readonly pagina: P;
}

export interface OpcionesRouter<P> {
  readonly rutas: readonly DefinicionRuta<P>[];
  /** Redirecciones exactas por path; reemplazan la entrada del historial. */
  readonly redirecciones?: Readonly<Record<string, string>>;
  /**
   * Guarda de acceso (C-07): corre después de las redirecciones fijas y antes de cargar la página. Devuelve otra
   * ruta para redirigir (reemplaza la entrada del historial) o null para seguir. Puede ser asíncrona (restaurar
   * la sesión): mientras tanto no se muestra nada de la ruta pedida.
   */
  readonly guarda?: (ruta: { readonly pathname: string; readonly ruta: string }) => string | null | Promise<string | null>;
  readonly noEncontrada: () => P | Promise<P>;
  readonly mostrar: (resolucion: Resolucion<P>) => void;
}

export interface Router {
  iniciar(): Promise<void>;
  detener(): void;
  navegar(ruta: string, opciones?: { reemplazar?: boolean }): Promise<void>;
  recargar(): Promise<void>;
  rutaActual(): string;
}

/** `/login?volver=<ruta>`, conservando las barras de la ruta para que se lea bien. */
export function rutaDeLogin(volver: string): string {
  return `/login?volver=${encodeURIComponent(volver).replace(/%2F/gi, '/')}`;
}

interface Compilada<P> {
  readonly definicion: DefinicionRuta<P>;
  readonly expresion: RegExp;
  readonly nombres: readonly string[];
}

function compilar<P>(definicion: DefinicionRuta<P>): Compilada<P> {
  const nombres: string[] = [];
  const cuerpo = definicion.patron
    .split('/')
    .map((segmento) => {
      if (!segmento.startsWith(':')) return segmento.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
      nombres.push(segmento.slice(1));
      return '([^/]+)';
    })
    .join('/');
  return { definicion, expresion: new RegExp(`^${cuerpo}/?$`), nombres };
}

const rutaDelNavegador = () => `${location.pathname}${location.search}`;

export function crearRouter<P>(opciones: OpcionesRouter<P>): Router {
  const compiladas = opciones.rutas.map(compilar);
  let generacion = 0;

  const MAXIMO_DE_REDIRECCIONES = 5;

  async function resolver(): Promise<void> {
    const actual = ++generacion;

    for (let saltos = 0; ; saltos += 1) {
      if (saltos > MAXIMO_DE_REDIRECCIONES) throw new Error(`La guarda generó un ciclo de redirecciones en ${location.pathname}`);
      const destino = opciones.redirecciones?.[location.pathname];
      if (destino !== undefined) history.replaceState(null, '', destino);
      if (opciones.guarda === undefined) break;
      const redireccion = await opciones.guarda({ pathname: location.pathname, ruta: rutaDelNavegador() });
      if (actual !== generacion) return; // se navegó a otra ruta mientras la guarda decidía
      if (redireccion === null) break;
      history.replaceState(null, '', redireccion);
    }

    const ruta = rutaDelNavegador();

    let patron: string | null = null;
    let params: Record<string, string> = {};
    let carga: P | Promise<P> | undefined;
    for (const c of compiladas) {
      const coincidencia = c.expresion.exec(location.pathname);
      if (coincidencia === null) continue;
      patron = c.definicion.patron;
      params = Object.fromEntries(c.nombres.map((n, i) => [n, decodeURIComponent(coincidencia[i + 1] ?? '')]));
      carga = c.definicion.cargar(params);
      break;
    }
    const pagina = await (carga ?? opciones.noEncontrada());
    if (actual !== generacion) return; // se navegó a otra ruta mientras cargaba
    opciones.mostrar({ ruta, patron, params, pagina });
  }

  const alPopstate = () => void resolver();

  function alClic(evento: MouseEvent): void {
    if (evento.defaultPrevented || evento.button !== 0) return;
    if (evento.metaKey || evento.ctrlKey || evento.shiftKey || evento.altKey) return;
    const objetivo = evento.target instanceof Element ? evento.target.closest<HTMLAnchorElement>('a[data-link]') : null;
    if (objetivo === null || (objetivo.target !== '' && objetivo.target !== '_self')) return;
    const url = new URL(objetivo.href, location.href);
    if (url.origin !== location.origin) return;
    evento.preventDefault();
    void navegar(`${url.pathname}${url.search}`);
  }

  async function navegar(ruta: string, { reemplazar = false } = {}): Promise<void> {
    if (reemplazar) history.replaceState(null, '', ruta);
    else if (ruta !== rutaDelNavegador()) history.pushState(null, '', ruta);
    await resolver();
  }

  return {
    async iniciar() {
      window.addEventListener('popstate', alPopstate);
      document.addEventListener('click', alClic);
      await resolver();
    },
    detener() {
      window.removeEventListener('popstate', alPopstate);
      document.removeEventListener('click', alClic);
    },
    navegar,
    recargar: resolver,
    rutaActual: rutaDelNavegador,
  };
}
