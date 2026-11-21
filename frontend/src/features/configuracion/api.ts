import type { components } from '../../shared/api-types';
import type { ClienteHttp } from '../../shared/http';
import type { ConfiguracionRequest, ConfiguracionResponse } from './unidades';

/** Endpoints de C-08 que usa Configuración (C-09, design D1), tipados con `api-types.ts`. */

export type PrestacionRequest = components['schemas']['PrestacionRequest'];
export type PrestacionResponse = components['schemas']['PrestacionResponse'];
export type Franja = components['schemas']['FranjaDto'];
type GrillaResponse = components['schemas']['GrillaResponse'];

const RUTA_CONSULTORIO = '/api/v1/consultorio';
const RUTA_PRESTACIONES = '/api/v1/prestaciones';
const RUTA_DISPONIBILIDAD = '/api/v1/disponibilidad';

const rutaDePrestacion = (id: string) => `${RUTA_PRESTACIONES}/${encodeURIComponent(id)}`;

export const obtenerConfiguracion = (http: ClienteHttp) => http.get<ConfiguracionResponse>(RUTA_CONSULTORIO);

export const guardarConfiguracion = (http: ClienteHttp, solicitud: ConfiguracionRequest) => http.put<ConfiguracionResponse>(RUTA_CONSULTORIO, solicitud);

export function listarPrestaciones(http: ClienteHttp, opciones: { readonly incluirInactivas?: boolean } = {}): Promise<PrestacionResponse[]> {
  return http.get<PrestacionResponse[]>(opciones.incluirInactivas === true ? `${RUTA_PRESTACIONES}?incluirInactivas=true` : RUTA_PRESTACIONES);
}

export const crearPrestacion = (http: ClienteHttp, solicitud: PrestacionRequest) => http.post<PrestacionResponse>(RUTA_PRESTACIONES, solicitud);

export const editarPrestacion = (http: ClienteHttp, id: string, solicitud: PrestacionRequest) => http.put<PrestacionResponse>(rutaDePrestacion(id), solicitud);

export const desactivarPrestacion = (http: ClienteHttp, id: string) => http.post<PrestacionResponse>(`${rutaDePrestacion(id)}/desactivar`);

export const activarPrestacion = (http: ClienteHttp, id: string) => http.post<PrestacionResponse>(`${rutaDePrestacion(id)}/activar`);

export async function obtenerGrilla(http: ClienteHttp): Promise<Franja[]> {
  const respuesta = await http.get<GrillaResponse>(RUTA_DISPONIBILIDAD);
  return (respuesta.franjas ?? []).flatMap((f) =>
    f.diaSemana === undefined || f.horaInicio === undefined || f.horaFin === undefined ? [] : [{ diaSemana: f.diaSemana, horaInicio: f.horaInicio, horaFin: f.horaFin }],
  );
}

export const guardarGrilla = (http: ClienteHttp, franjas: readonly Franja[]) => http.put<GrillaResponse>(RUTA_DISPONIBILIDAD, { franjas });
