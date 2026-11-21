import type { components } from '../../shared/api-types';
import { ApiError, type ClienteHttp } from '../../shared/http';
import type { BloqueoAgenda, CambioPaciente, EstadoTurno, FranjaDisponible, TurnoAgenda } from './modelo';
import { instanteLocal, sumarDias } from './tiempo';

/**
 * Cliente de la agenda (C-13, design D2), tipado con `api-types.ts`. La respuesta llega sin validar: cada entrada se
 * revisa y se descarta si no trae lo mínimo para dibujarse. Sin conexión, el Service Worker de C-06 responde
 * `GET /api/v1/agenda` con la última copia guardada; si no hay copia, el pedido falla con `SIN_CONEXION`.
 */

type AgendaResponse = components['schemas']['AgendaResponse'];
type TurnoResponse = components['schemas']['TurnoResponse'];
type BloqueoResponse = components['schemas']['BloqueoResponse'];
type DetalleResponse = components['schemas']['DetalleResponse'];
type GrillaResponse = components['schemas']['GrillaResponse'];

export interface Agenda {
  readonly turnos: TurnoAgenda[];
  readonly bloqueos: BloqueoAgenda[];
}

export interface DetalleTurno extends TurnoAgenda {
  readonly paciente: { readonly nombre: string; readonly apellido: string; readonly dni: string | null; readonly telefono: string | null; readonly email: string | null };
  readonly notaInterna: string | null;
  readonly motivoCancelacion: string | null;
  readonly horarioAnterior: { readonly inicio: string; readonly fin: string } | null;
  readonly version: number | null;
}

const ESTADOS: readonly EstadoTurno[] = ['RESERVADO', 'CONFIRMADO', 'CANCELADO', 'AUSENTE'];
const CAMBIOS: readonly CambioPaciente[] = ['RESERVO', 'REPROGRAMO', 'CANCELO'];

const esEstado = (valor: unknown): valor is EstadoTurno => ESTADOS.includes(valor as EstadoTurno);
const esCambio = (valor: unknown): valor is CambioPaciente => CAMBIOS.includes(valor as CambioPaciente);
const texto = (valor: string | undefined) => (valor === undefined || valor === '' ? null : valor);

function aTurno(t: TurnoResponse): TurnoAgenda | null {
  if (t.id === undefined || t.inicio === undefined || t.fin === undefined || !esEstado(t.estado)) return null;
  return {
    id: t.id,
    inicio: t.inicio,
    fin: t.fin,
    duracionMin: t.duracionMin ?? Math.round((Date.parse(t.fin) - Date.parse(t.inicio)) / 60000),
    estado: t.estado,
    esSobreturno: t.esSobreturno === true,
    origen: t.origen === 'LINK' ? 'LINK' : 'MANUAL',
    prestacion: t.prestacion?.nombre ?? '',
    paciente: { nombre: t.paciente?.nombre ?? '', apellido: t.paciente?.apellido ?? '' },
    tieneNota: t.tieneNota === true,
    canceladoPor: t.canceladoPor ?? null,
    cambioPaciente: esCambio(t.cambioPaciente) ? t.cambioPaciente : null,
    novedadPacienteAt: t.novedadPacienteAt ?? null,
  };
}

function aBloqueo(b: BloqueoResponse): BloqueoAgenda | null {
  if (b.id === undefined || b.inicio === undefined || b.fin === undefined) return null;
  return { id: b.id, inicio: b.inicio, fin: b.fin, motivo: texto(b.motivo) };
}

const sinNulos = <T,>(lista: readonly (T | null)[]): T[] => lista.filter((x): x is T => x !== null);

/**
 * La semana completa de lunes a lunes (`[desde, hasta)`) en la zona del consultorio. Día y Semana piden el mismo
 * rango: así la tira de días tiene sus conteos y la copia sin conexión sirve para las dos vistas.
 */
export function obtenerAgendaDeSemana(http: ClienteHttp, lunes: string, zona: string): Promise<Agenda> {
  return obtenerAgendaEntre(http, instanteLocal(lunes, '00:00', zona).toISOString(), instanteLocal(sumarDias(lunes, 7), '00:00', zona).toISOString());
}

/** Turnos (en cualquier estado) y bloqueos que se cruzan con `[desde, hasta)`, instantes UTC. */
export async function obtenerAgendaEntre(http: ClienteHttp, desde: string, hasta: string): Promise<Agenda> {
  const respuesta = await http.get<AgendaResponse | undefined>(`/api/v1/agenda?desde=${encodeURIComponent(desde)}&hasta=${encodeURIComponent(hasta)}`);
  return { turnos: sinNulos((respuesta?.turnos ?? []).map(aTurno)), bloqueos: sinNulos((respuesta?.bloqueos ?? []).map(aBloqueo)) };
}

let franjasEnMemoria: FranjaDisponible[] | null = null;

/**
 * Horario de atención (C-08), en memoria durante la sesión (D2). No lo guarda el Service Worker: sin conexión y sin
 * copia en memoria devuelve null y la agenda se muestra sin huecos libres.
 */
export async function obtenerFranjas(http: ClienteHttp): Promise<FranjaDisponible[] | null> {
  if (franjasEnMemoria !== null) return franjasEnMemoria;
  try {
    const respuesta = await http.get<GrillaResponse | undefined>('/api/v1/disponibilidad');
    franjasEnMemoria = (respuesta?.franjas ?? []).flatMap((f) =>
      f.diaSemana === undefined || f.horaInicio === undefined || f.horaFin === undefined ? [] : [{ diaSemana: f.diaSemana, horaInicio: f.horaInicio, horaFin: f.horaFin }],
    );
    return franjasEnMemoria;
  } catch (error) {
    if (error instanceof ApiError && error.codigo === 'SIN_CONEXION') return null;
    throw error;
  }
}

export function olvidarFranjas(): void {
  franjasEnMemoria = null;
}

/** Detalle completo del turno (`GET /api/v1/turnos/{id}`, decidido por Maxi en #27). */
export async function obtenerTurno(http: ClienteHttp, id: string): Promise<DetalleTurno> {
  const respuesta = await http.get<DetalleResponse | undefined>(`/api/v1/turnos/${encodeURIComponent(id)}`);
  const base = respuesta === undefined ? null : aTurno(respuesta);
  if (respuesta === undefined || base === null) throw new ApiError(200, 'ERROR_INESPERADO', 'La respuesta del servidor no es válida.');
  const anterior = respuesta.horarioAnterior;
  return {
    ...base,
    paciente: {
      nombre: base.paciente.nombre,
      apellido: base.paciente.apellido,
      dni: texto(respuesta.paciente?.dni),
      telefono: texto(respuesta.paciente?.telefono),
      email: texto(respuesta.paciente?.email),
    },
    notaInterna: texto(respuesta.notaInterna),
    motivoCancelacion: texto(respuesta.motivoCancelacion),
    horarioAnterior: anterior?.inicio === undefined || anterior.fin === undefined ? null : { inicio: anterior.inicio, fin: anterior.fin },
    version: respuesta.version ?? null,
  };
}

/** Deja como visto el cambio del paciente que se mostró (DD-30). Manda el instante recibido, como pide C-12 (D5). */
export async function marcarVisto(http: ClienteHttp, id: string, novedadAt: string): Promise<void> {
  await http.post<undefined>(`/api/v1/turnos/${encodeURIComponent(id)}/visto`, { novedadAt });
}
