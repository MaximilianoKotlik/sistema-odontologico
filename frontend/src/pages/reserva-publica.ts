import type { ContextoPublico } from '../publico';

/** Página pública `/c/{slug}` (C-16). */
export async function montarReservaPublica(_contenedor: HTMLElement, { http, params }: ContextoPublico): Promise<void> {
  await http.get<unknown>(`/api/v1/public/consultorios/${encodeURIComponent(params['slug'] ?? '')}`).catch(() => undefined);
}
