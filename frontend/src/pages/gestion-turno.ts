import { montarGestion } from '../features/gestion-turno/pantalla';
import type { ContextoPublico } from '../publico';

/** Página pública `/t/{token}` (C-16, spec pantalla-gestion-turno). */
export function montarGestionTurno(contenedor: HTMLElement, { http, params, cabecera }: ContextoPublico): Promise<void> {
  return montarGestion(contenedor, { http, token: params['token'] ?? '', cabecera });
}
