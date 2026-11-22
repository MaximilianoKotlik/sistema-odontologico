import { ApiError } from '../../shared/http';

/**
 * Traducción de los errores de la API pública de C-15 a estados de pantalla (C-16, design D8). La usan la reserva
 * (`/c/{slug}`) y la gestión (`/t/{token}`). Nunca se muestra el código ni el `detail` del servidor.
 */
export type CampoDelPedido = 'nombre' | 'apellido' | 'dni' | 'telefono' | 'email';

export type EstadoDeError =
  | { readonly tipo: 'hueco-ocupado'; readonly huecos: readonly string[] }
  | { readonly tipo: 'limite-turnos' }
  | { readonly tipo: 'reserva-no-disponible'; readonly telefono: string | null }
  | { readonly tipo: 'fuera-de-plazo'; readonly telefono: string | null }
  | { readonly tipo: 'conflicto' }
  | { readonly tipo: 'no-encontrado' }
  | { readonly tipo: 'campos'; readonly campos: readonly CampoDelPedido[] }
  | { readonly tipo: 'demasiadas'; readonly segundos: number }
  | { readonly tipo: 'sin-conexion' }
  | { readonly tipo: 'inesperado' };

const CAMPOS: readonly CampoDelPedido[] = ['nombre', 'apellido', 'dni', 'telefono', 'email'];
const esCampo = (valor: unknown): valor is CampoDelPedido => typeof valor === 'string' && (CAMPOS as readonly string[]).includes(valor);
const SEGUNDOS_POR_DEFECTO = 60;

function textos(valor: unknown): string[] {
  return Array.isArray(valor) ? valor.filter((v): v is string => typeof v === 'string') : [];
}

function telefono(datos: Readonly<Record<string, unknown>>): string | null {
  const valor = datos['telefonoContacto'];
  return typeof valor === 'string' && valor !== '' ? valor : null;
}

function camposDeValidacion(datos: Readonly<Record<string, unknown>>): CampoDelPedido[] {
  const errores = datos['errores'];
  if (!Array.isArray(errores)) return [];
  return errores.flatMap((error: unknown) => {
    if (typeof error !== 'object' || error === null) return [];
    const campo = (error as Record<string, unknown>)['campo'];
    return esCampo(campo) ? [campo] : [];
  });
}

/** `Retry-After` en segundos; la forma de fecha HTTP y los valores raros caen en un minuto. */
function segundosDeEspera(retryAfter: string | null): number {
  if (retryAfter === null || !/^\d+$/.test(retryAfter.trim())) return SEGUNDOS_POR_DEFECTO;
  const segundos = Number(retryAfter.trim());
  return segundos > 0 ? segundos : SEGUNDOS_POR_DEFECTO;
}

export function traducirErrorPublico(error: unknown): EstadoDeError {
  if (!(error instanceof ApiError)) return { tipo: 'inesperado' };
  if (error.status === 404) return { tipo: 'no-encontrado' };
  if (error.status === 429) return { tipo: 'demasiadas', segundos: segundosDeEspera(error.retryAfter) };
  switch (error.codigo) {
    case 'HUECO_NO_DISPONIBLE':
      return { tipo: 'hueco-ocupado', huecos: textos(error.datos['huecos']) };
    case 'LIMITE_TURNOS':
      return { tipo: 'limite-turnos' };
    case 'RESERVA_NO_DISPONIBLE':
      return { tipo: 'reserva-no-disponible', telefono: telefono(error.datos) };
    case 'FUERA_DE_PLAZO':
      return { tipo: 'fuera-de-plazo', telefono: telefono(error.datos) };
    case 'CONFLICTO_CONCURRENCIA':
      return { tipo: 'conflicto' };
    case 'DNI_INVALIDO':
      return { tipo: 'campos', campos: ['dni'] };
    case 'TELEFONO_INVALIDO':
      return { tipo: 'campos', campos: ['telefono'] };
    case 'VALIDACION':
      return { tipo: 'campos', campos: camposDeValidacion(error.datos) };
    case 'SIN_CONEXION':
      return { tipo: 'sin-conexion' };
    default:
      return { tipo: 'inesperado' };
  }
}

/** Texto del 429: siempre en minutos enteros, como mínimo uno. */
export function mensajeDeEspera(segundos: number): string {
  const minutos = Math.max(1, Math.ceil(segundos / 60));
  return minutos === 1 ? 'Esperá un minuto y volvé a intentar.' : `Esperá ${minutos} minutos y volvé a intentar.`;
}
