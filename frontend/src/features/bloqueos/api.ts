import type { components } from '../../shared/api-types';
import { ApiError, type ClienteHttp } from '../../shared/http';
import { aBloqueo, sinNulos } from '../agenda/api';
import type { BloqueoAgenda } from '../agenda/modelo';

/**
 * Cliente de bloqueos (C-14, design D8; C-12). Un bloqueo que pisa turnos activos responde 409 `BLOQUEO_CON_TURNOS`
 * con los ids en `turnosEnConflicto` (RN-AG-05).
 */

type BloqueoResponse = components['schemas']['BloqueoResponse'];
type CrearBloqueoRequest = components['schemas']['CrearBloqueoRequest'];

const RUTA = '/api/v1/bloqueos';

/** Los bloqueos que siguen vigentes desde ahora (actuales y futuros). */
export async function listarBloqueos(http: ClienteHttp): Promise<BloqueoAgenda[]> {
  return sinNulos(((await http.get<BloqueoResponse[] | undefined>(RUTA)) ?? []).map(aBloqueo));
}

export async function crearBloqueo(http: ClienteHttp, pedido: { readonly inicio: string; readonly fin: string; readonly motivo: string }): Promise<BloqueoAgenda> {
  const motivo = pedido.motivo.trim();
  const cuerpo: CrearBloqueoRequest = { inicio: pedido.inicio, fin: pedido.fin, ...(motivo === '' ? {} : { motivo }) };
  const creado = aBloqueo((await http.post<BloqueoResponse | undefined>(RUTA, cuerpo)) ?? {});
  if (creado === null) throw new ApiError(201, 'ERROR_INESPERADO', 'La respuesta del servidor no es válida.');
  return creado;
}

export async function eliminarBloqueo(http: ClienteHttp, id: string): Promise<void> {
  await http.delete<undefined>(`${RUTA}/${encodeURIComponent(id)}`);
}
