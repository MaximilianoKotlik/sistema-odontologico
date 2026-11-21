/**
 * Cliente HTTP de la app (spec cliente-api, design D2).
 * - Access token SOLO en memoria (cierre de `crearClienteHttp`); nunca en storage ni cookies legibles.
 * - `Authorization` solo en rutas privadas; cookies (`credentials: include`) solo en /api/v1/auth/**.
 * - Ante 401 privado: un único refresh compartido y un único reintento.
 * - Errores normalizados a `ApiError` (Problem Details RFC 7807).
 *
 * Los cuerpos y respuestas de cada endpoint se tipan con `api-types.ts` (generado, no editar)
 * a medida que C-04/C-12 publican el contrato.
 */

const PREFIJO_AUTH = '/api/v1/auth/';
const PREFIJO_PUBLICO = '/api/v1/public/';
const RUTA_REFRESH = '/api/v1/auth/refresh';

/** Códigos que genera el propio cliente (los de dominio vienen del backend). */
export type CodigoCliente = 'SIN_CONEXION' | 'SESION_VENCIDA' | 'ERROR_INESPERADO' | 'DESCARGA_INTERRUMPIDA';

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly codigo: string,
    readonly detail: string,
    /** Propiedades extra del Problem Details (p. ej. `minutosRestantes` de `CUENTA_BLOQUEADA`). */
    readonly datos: Readonly<Record<string, unknown>> = {},
    /** Valor crudo de `Retry-After` en un 429, o null. */
    readonly retryAfter: string | null = null,
  ) {
    super(detail);
    this.name = 'ApiError';
  }
}

/**
 * Mensaje propio del 403 `PLAN_VENCIDO` (C-20, spec aviso-de-plan): reemplaza el `detail` del servidor, que es para
 * desarrolladores y no está en el registro de la UI.
 */
export const MENSAJE_PLAN_VENCIDO =
  'No se guardó: tu prueba terminó y la agenda pasó a solo lectura. No se perdió nada de lo que ya tenías.';

export type Metodo = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

export interface OpcionesCliente {
  readonly fetch?: typeof fetch;
  /** Se llama una vez cuando la sesión no se puede renovar (la app navega a /login). */
  readonly alVencerSesion?: () => void;
  /** Se llama con cada respuesta exitosa (p. ej. para registrar la hora de la última agenda). */
  readonly alResponder?: (ruta: string, respuesta: Response) => void;
  /** Se llama cuando un pedido no llega al servidor por falta de red (navigator.onLine no es confiable). */
  readonly alFallarRed?: () => void;
  /** Se llama cuando una escritura responde 403 `PLAN_VENCIDO`: la app vuelve a pedir el plan (C-20). No se reintenta. */
  readonly alPlanVencido?: () => void;
  /**
   * Serializa el refresh entre pestañas (C-07, D6). Recibe la tarea completa y la corre bajo el lock; la tarea
   * termina recién con la respuesta leída, así el lock no se libera antes de guardar la cookie rotada.
   */
  readonly coordinarRefresh?: <T>(tarea: () => Promise<T>) => Promise<T>;
}

export interface OpcionesRenovacion {
  /** false en la restauración al recargar (D3): un 401 es el caso normal de alguien sin sesión, no un aviso. */
  readonly avisarSiVence?: boolean;
}

/** Archivo recibido de un pedido privado (C-19). */
export interface ArchivoDescargado {
  readonly contenido: Blob;
  readonly nombre: string;
}

export interface ClienteHttp {
  request<T>(metodo: Metodo, ruta: string, cuerpo?: unknown): Promise<T>;
  /** Pedido cuya respuesta es un archivo: misma autorización, renovación y errores que los pedidos JSON. */
  descargar(ruta: string, opciones: { readonly metodo: Metodo }): Promise<ArchivoDescargado>;
  get<T>(ruta: string): Promise<T>;
  post<T>(ruta: string, cuerpo?: unknown): Promise<T>;
  put<T>(ruta: string, cuerpo?: unknown): Promise<T>;
  patch<T>(ruta: string, cuerpo?: unknown): Promise<T>;
  delete<T>(ruta: string): Promise<T>;
  /** Pide un access token nuevo con la cookie de refresh. Devuelve false si la sesión no es renovable. */
  renovarSesion(opciones?: OpcionesRenovacion): Promise<boolean>;
  establecerToken(token: string): void;
  borrarToken(): void;
  tieneToken(): boolean;
}

function esObjeto(valor: unknown): valor is Record<string, unknown> {
  return typeof valor === 'object' && valor !== null;
}

function tieneAccessToken(valor: unknown): valor is { accessToken: string } {
  return esObjeto(valor) && typeof valor['accessToken'] === 'string' && valor['accessToken'] !== '';
}

/** Rutas de auth que además exigen la sesión (Bearer): cambiar la contraseña con la sesión iniciada (US-005). */
const RUTAS_AUTH_CON_SESION: readonly string[] = ['/api/v1/auth/cambiar-password'];

const esRutaAuth = (ruta: string) => ruta.startsWith(PREFIJO_AUTH);
const esRutaPrivada = (ruta: string) =>
  RUTAS_AUTH_CON_SESION.includes(ruta) || (ruta.startsWith('/api/v1/') && !esRutaAuth(ruta) && !ruta.startsWith(PREFIJO_PUBLICO));

async function leerCuerpo(respuesta: Response): Promise<unknown> {
  if (respuesta.status === 204) return undefined;
  const texto = await respuesta.text();
  if (texto === '') return undefined;
  const tipo = respuesta.headers.get('Content-Type') ?? '';
  if (!tipo.includes('json')) return texto;
  try {
    return JSON.parse(texto) as unknown;
  } catch {
    throw new ApiError(respuesta.status, 'ERROR_INESPERADO', 'La respuesta del servidor no es válida.');
  }
}

const CLAVES_ESTANDAR = new Set(['type', 'title', 'status', 'detail', 'instance', 'codigo']);

async function aApiError(respuesta: Response): Promise<ApiError> {
  const tipo = respuesta.headers.get('Content-Type') ?? '';
  const retryAfter = respuesta.headers.get('Retry-After');
  if (tipo.includes('application/problem+json') || tipo.includes('application/json')) {
    const cuerpo: unknown = await respuesta.json().catch(() => undefined);
    if (esObjeto(cuerpo) && typeof cuerpo['codigo'] === 'string') {
      const detail = typeof cuerpo['detail'] === 'string' ? cuerpo['detail'] : '';
      const datos = Object.fromEntries(Object.entries(cuerpo).filter(([clave]) => !CLAVES_ESTANDAR.has(clave)));
      return new ApiError(respuesta.status, cuerpo['codigo'], detail, datos, retryAfter);
    }
  }
  return new ApiError(respuesta.status, 'ERROR_INESPERADO', `Error HTTP ${respuesta.status}`, {}, retryAfter);
}

const ACEPTA_JSON = 'application/json, application/problem+json';
const ACEPTA_ARCHIVO = 'application/zip, application/octet-stream, application/problem+json';

/** Parser acotado a `filename="…"`: es el formato que emite el backend (C-19, design D10). */
function nombreDeArchivo(respuesta: Response): string {
  return /filename="([^"]+)"/.exec(respuesta.headers.get('Content-Disposition') ?? '')?.[1] ?? 'descarga';
}

export function crearClienteHttp(opciones: OpcionesCliente = {}): ClienteHttp {
  const hacerFetch = opciones.fetch ?? ((entrada, init) => fetch(entrada, init));
  const coordinar = opciones.coordinarRefresh ?? (<T,>(tarea: () => Promise<T>) => tarea());
  let tokenEnMemoria: string | null = null;
  let refreshEnCurso: Promise<boolean> | null = null;
  let avisoEnCurso: Promise<boolean> | null = null;

  async function enviar(metodo: Metodo, ruta: string, cuerpo: unknown, acepta = ACEPTA_JSON): Promise<Response> {
    const encabezados = new Headers({ Accept: acepta });
    if (cuerpo !== undefined) encabezados.set('Content-Type', 'application/json');
    if (esRutaPrivada(ruta) && tokenEnMemoria !== null) encabezados.set('Authorization', `Bearer ${tokenEnMemoria}`);

    const init: RequestInit = {
      method: metodo,
      headers: encabezados,
      credentials: esRutaAuth(ruta) ? 'include' : 'same-origin',
    };
    if (cuerpo !== undefined) init.body = JSON.stringify(cuerpo);

    try {
      return await hacerFetch(ruta, init);
    } catch {
      opciones.alFallarRed?.();
      throw new ApiError(0, 'SIN_CONEXION', 'No hay conexión con el servidor.');
    }
  }

  async function pedirRefresh(): Promise<boolean> {
    const respuesta = await enviar('POST', RUTA_REFRESH, undefined);
    if (!respuesta.ok) return false;
    // Un cuerpo ilegible cuenta como sesión no renovable, igual que un cuerpo sin token.
    const cuerpo = await leerCuerpo(respuesta).catch(() => undefined);
    if (!tieneAccessToken(cuerpo)) return false;
    tokenEnMemoria = cuerpo.accessToken;
    return true;
  }

  function vencerSesion(): void {
    tokenEnMemoria = null;
    opciones.alVencerSesion?.();
  }

  /**
   * Un único refresh en vuelo por pestaña, serializado entre pestañas por `coordinarRefresh` (D6). Sin
   * AbortController ni timeout: abortarlo después de que el backend rotó la cookie perdería el Set-Cookie.
   */
  function refrescar(): Promise<boolean> {
    refreshEnCurso ??= coordinar(pedirRefresh).finally(() => {
      refreshEnCurso = null;
    });
    return refreshEnCurso;
  }

  /** Si la renovación falla, el aviso de sesión vencida se emite una sola vez aunque haya varios pedidos. */
  function renovarSesion({ avisarSiVence = true }: OpcionesRenovacion = {}): Promise<boolean> {
    if (!avisarSiVence) {
      return refrescar().then((renovada) => {
        if (!renovada) tokenEnMemoria = null;
        return renovada;
      });
    }
    avisoEnCurso ??= refrescar()
      .then((renovada) => {
        if (!renovada) vencerSesion();
        return renovada;
      })
      .finally(() => {
        avisoEnCurso = null;
      });
    return avisoEnCurso;
  }

  const sesionVencida = () => new ApiError(401, 'SESION_VENCIDA', 'Tu sesión venció. Iniciá sesión de nuevo.');

  /** Envía el pedido con la renovación ante 401 y devuelve solo una respuesta exitosa. */
  async function pedir(metodo: Metodo, ruta: string, cuerpo: unknown, acepta?: string): Promise<Response> {
    let respuesta = await enviar(metodo, ruta, cuerpo, acepta);

    if (respuesta.status === 401 && esRutaPrivada(ruta)) {
      if (!(await renovarSesion())) throw sesionVencida();
      respuesta = await enviar(metodo, ruta, cuerpo, acepta);
      if (respuesta.status === 401) {
        vencerSesion();
        throw sesionVencida();
      }
    }

    if (!respuesta.ok) {
      const error = await aApiError(respuesta);
      if (error.status === 403 && error.codigo === 'PLAN_VENCIDO') {
        opciones.alPlanVencido?.();
        throw new ApiError(403, error.codigo, MENSAJE_PLAN_VENCIDO, {}, null);
      }
      throw error;
    }
    opciones.alResponder?.(ruta, respuesta);
    return respuesta;
  }

  async function request<T>(metodo: Metodo, ruta: string, cuerpo?: unknown): Promise<T> {
    const respuesta = await pedir(metodo, ruta, cuerpo);
    // El tipo T lo fija quien llama a partir de api-types.ts; el cuerpo llega como JSON sin validar.
    return (await leerCuerpo(respuesta)) as T;
  }

  async function descargar(ruta: string, { metodo }: { readonly metodo: Metodo }): Promise<ArchivoDescargado> {
    const respuesta = await pedir(metodo, ruta, undefined, ACEPTA_ARCHIVO);
    try {
      return { contenido: await respuesta.blob(), nombre: nombreDeArchivo(respuesta) };
    } catch {
      throw new ApiError(respuesta.status, 'DESCARGA_INTERRUMPIDA', 'La descarga se interrumpió.');
    }
  }

  return {
    request,
    descargar,
    get: (ruta) => request('GET', ruta),
    post: (ruta, cuerpo) => request('POST', ruta, cuerpo),
    put: (ruta, cuerpo) => request('PUT', ruta, cuerpo),
    patch: (ruta, cuerpo) => request('PATCH', ruta, cuerpo),
    delete: (ruta) => request('DELETE', ruta),
    renovarSesion,
    establecerToken: (token) => {
      tokenEnMemoria = token;
    },
    borrarToken: () => {
      tokenEnMemoria = null;
    },
    tieneToken: () => tokenEnMemoria !== null,
  };
}
