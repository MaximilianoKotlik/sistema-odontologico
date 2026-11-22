import { montarReserva } from '../features/reserva-publica/pantalla';
import type { ContextoPublico } from '../publico';

/** Página pública `/c/{slug}` (C-16, spec pantalla-reserva-publica). */
export function montarReservaPublica(contenedor: HTMLElement, { http, params, cabecera }: ContextoPublico): Promise<void> {
  return montarReserva(contenedor, { http, slug: params['slug'] ?? '', cabecera });
}
