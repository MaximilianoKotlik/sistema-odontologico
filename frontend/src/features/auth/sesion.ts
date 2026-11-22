/**
 * Guardas de acceso (C-07, design D2 y D3, aprobadas por Maxi en #37). Función pura: la app decide con ella y se
 * encarga de restaurar la sesión, mostrar el splash y navegar.
 */

export type DecisionDeAcceso =
  | { readonly tipo: 'seguir' }
  | { readonly tipo: 'restaurar' }
  | { readonly tipo: 'redirigir'; readonly a: string };

const RUTAS_DE_INGRESO: readonly string[] = ['/login', '/registro'];
const RUTAS_PUBLICAS: readonly string[] = [...RUTAS_DE_INGRESO, '/recuperar', '/terminos', '/privacidad'];

export function esRutaPrivada(pathname: string): boolean {
  return pathname === '/app' || pathname.startsWith('/app/');
}

export function esRutaPublicaDeAuth(pathname: string): boolean {
  return RUTAS_PUBLICAS.includes(pathname);
}

export function decidirAcceso(pathname: string, tieneToken: boolean): DecisionDeAcceso {
  if (esRutaPrivada(pathname)) return tieneToken ? { tipo: 'seguir' } : { tipo: 'restaurar' };
  // D3 (opción a): sin token en memoria, login y registro muestran el formulario sin preguntar al backend.
  if (RUTAS_DE_INGRESO.includes(pathname)) return tieneToken ? { tipo: 'redirigir', a: '/app' } : { tipo: 'seguir' };
  if (RUTAS_PUBLICAS.includes(pathname)) return { tipo: 'seguir' };
  return { tipo: 'redirigir', a: tieneToken ? '/app' : '/login' };
}
