import type { ContextoPublico } from '../publico';

/** Página pública `/t/{token}` (C-16). */
export async function montarGestionTurno(_contenedor: HTMLElement, { http, params }: ContextoPublico): Promise<void> {
  await http.get<unknown>(`/api/v1/public/turnos/${encodeURIComponent(params['token'] ?? '')}`).catch(() => undefined);
}
