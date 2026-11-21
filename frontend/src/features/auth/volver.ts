/**
 * Destino después del login (C-07, design D5, aprobada por Maxi en #37). Valida después de normalizar: el parser
 * de URL resuelve `..`, `%2e%2e` y `\`, así que el chequeo se hace sobre la ruta resultante y no sobre el texto.
 */

const DESTINO_POR_DEFECTO = '/app';

export function sanearVolver(valor: string | null, origen: string = location.origin): string {
  if (valor === null || !valor.startsWith('/')) return DESTINO_POR_DEFECTO;

  let url: URL;
  try {
    url = new URL(valor, origen);
  } catch {
    return DESTINO_POR_DEFECTO;
  }

  if (url.origin !== new URL(origen).origin) return DESTINO_POR_DEFECTO;
  if (url.pathname !== '/app' && !url.pathname.startsWith('/app/')) return DESTINO_POR_DEFECTO;
  return url.pathname + url.search + url.hash;
}
