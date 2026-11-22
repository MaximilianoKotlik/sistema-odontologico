import type { components } from '../../shared/api-types';
import { ApiError, type ClienteHttp } from '../../shared/http';

/**
 * Cliente del historial y del registro de cambios (C-18, design D8). La respuesta llega sin validar: un evento que no
 * trae lo mínimo para dibujarse se descarta y no rompe la lista.
 */

type EventoResponse = components['schemas']['EventoResponse'];
type RegistroDeCambiosResponse = components['schemas']['RegistroDeCambiosResponse'];

export interface ActorAuditoria {
  readonly tipo: string;
  readonly id: string | null;
  readonly nombre: string | null;
  readonly identidadEnRevision: boolean;
}

export type Valores = Readonly<Record<string, unknown>>;

export interface EventoAuditoria {
  readonly id: string;
  readonly ocurridoAt: string;
  readonly actor: ActorAuditoria;
  readonly accion: string;
  readonly entidad: string;
  readonly entidadId: string | null;
  readonly valorAnterior: Valores | null;
  readonly valorNuevo: Valores | null;
  readonly ip: string | null;
  readonly contexto: { readonly inicio: string | null; readonly paciente: string | null } | null;
}

export interface PaginaDeRegistro {
  readonly eventos: EventoAuditoria[];
  readonly siguienteCursor: string | null;
}

export interface FiltrosDelRegistro {
  readonly turnoId?: string | null;
  readonly desde?: string | null;
  readonly hasta?: string | null;
  readonly acciones?: readonly string[];
}

function aEvento(e: EventoResponse): EventoAuditoria | null {
  if (e.id === undefined || e.ocurridoAt === undefined || e.accion === undefined || e.actor?.tipo === undefined) return null;
  return {
    id: e.id,
    ocurridoAt: e.ocurridoAt,
    actor: { tipo: e.actor.tipo, id: e.actor.id ?? null, nombre: e.actor.nombre ?? null, identidadEnRevision: e.actor.identidadEnRevision === true },
    accion: e.accion,
    entidad: e.entidad ?? '',
    entidadId: e.entidadId ?? null,
    valorAnterior: e.valorAnterior ?? null,
    valorNuevo: e.valorNuevo ?? null,
    ip: e.ip ?? null,
    contexto: e.contexto === undefined || e.contexto === null ? null : { inicio: e.contexto.inicio ?? null, paciente: e.contexto.paciente ?? null },
  };
}

const eventosValidos = (lista: readonly EventoResponse[]) => lista.map(aEvento).filter((e): e is EventoAuditoria => e !== null);

/** Eventos de un turno en orden cronológico. */
export async function obtenerHistorial(http: ClienteHttp, turnoId: string): Promise<EventoAuditoria[]> {
  const respuesta = await http.get<EventoResponse[] | undefined>(`/api/v1/turnos/${encodeURIComponent(turnoId)}/historial`);
  if (!Array.isArray(respuesta)) throw new ApiError(200, 'ERROR_INESPERADO', 'La respuesta del servidor no es válida.');
  return eventosValidos(respuesta);
}

/** Una página del registro, del evento más nuevo al más viejo. */
export async function obtenerRegistro(http: ClienteHttp, filtros: FiltrosDelRegistro, cursor: string | null, tamano = 50): Promise<PaginaDeRegistro> {
  const query = new URLSearchParams({ tamano: String(tamano) });
  if (filtros.turnoId) query.set('turnoId', filtros.turnoId);
  if (filtros.desde) query.set('desde', filtros.desde);
  if (filtros.hasta) query.set('hasta', filtros.hasta);
  for (const accion of filtros.acciones ?? []) query.append('accion', accion);
  if (cursor !== null) query.set('cursor', cursor);
  const respuesta = await http.get<RegistroDeCambiosResponse | undefined>(`/api/v1/auditoria?${query.toString()}`);
  if (respuesta === undefined || !Array.isArray(respuesta.eventos)) throw new ApiError(200, 'ERROR_INESPERADO', 'La respuesta del servidor no es válida.');
  return { eventos: eventosValidos(respuesta.eventos), siguienteCursor: respuesta.siguienteCursor ?? null };
}
