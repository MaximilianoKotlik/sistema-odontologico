import type { components } from '../../shared/api-types';
import type { ClienteHttp } from '../../shared/http';
import { aDetalle, aTurno, sinNulos, type DetalleTurno } from '../agenda/api';
import type { TurnoAgenda } from '../agenda/modelo';
import { listarPrestaciones, obtenerConfiguracion } from '../configuracion/api';
import { normalizarDni, type PacienteNuevo } from './validaciones';

/**
 * Cliente de las acciones sobre turnos (C-14, design D1), tipado con `api-types.ts`. Las escrituras mandan la
 * `version` leída (RN-GL-02) y responden el detalle con su versión nueva; los errores llegan como `ApiError` con los
 * datos del Problem Details para que `errores.ts` los explique.
 */

type DetalleResponse = components['schemas']['DetalleResponse'];
type TurnoResponse = components['schemas']['TurnoResponse'];
type PacienteResponse = components['schemas']['PacienteResponse'];
type CrearTurnoRequest = components['schemas']['CrearTurnoRequest'];
type TurnoCreadoResponse = components['schemas']['TurnoCreadoResponse'];
type CambiarEstadoRequest = components['schemas']['CambiarEstadoRequest'];

const rutaDelTurno = (id: string) => `/api/v1/turnos/${encodeURIComponent(id)}`;

export interface PacienteEncontrado {
  readonly id: string;
  readonly nombre: string;
  readonly apellido: string;
  readonly dni: string | null;
  readonly telefono: string | null;
}

/** Pacientes del consultorio por nombre, DNI o teléfono (`GET /api/v1/pacientes?q=`). No se guarda en el dispositivo. */
export async function buscarPacientes(http: ClienteHttp, texto: string): Promise<PacienteEncontrado[]> {
  const respuesta = await http.get<PacienteResponse[] | undefined>(`/api/v1/pacientes?q=${encodeURIComponent(texto)}`);
  return (respuesta ?? []).flatMap((p) =>
    p.id === undefined || p.nombre === undefined || p.apellido === undefined ? [] : [{ id: p.id, nombre: p.nombre, apellido: p.apellido, dni: p.dni ?? null, telefono: p.telefono ?? null }],
  );
}

export type PacienteDelTurno = { readonly tipo: 'existente'; readonly id: string } | { readonly tipo: 'nuevo'; readonly datos: PacienteNuevo };

export interface PedidoDeTurno {
  readonly paciente: PacienteDelTurno;
  readonly prestacionId: string;
  readonly inicio: string;
  readonly esSobreturno: boolean;
}

function pacienteNuevo(datos: PacienteNuevo): NonNullable<CrearTurnoRequest['pacienteNuevo']> {
  const email = datos.email.trim();
  return { nombre: datos.nombre.trim(), apellido: datos.apellido.trim(), dni: normalizarDni(datos.dni), telefono: datos.telefono.trim(), ...(email === '' ? {} : { email }) };
}

/** Alta de turno o sobreturno (FL-03). El fin lo calcula el backend con la prestación (RN-AG-12). */
export async function crearTurno(http: ClienteHttp, pedido: PedidoDeTurno): Promise<{ readonly turno: DetalleTurno; readonly pacienteReutilizado: boolean }> {
  const cuerpo: CrearTurnoRequest = {
    ...(pedido.paciente.tipo === 'existente' ? { pacienteId: pedido.paciente.id } : { pacienteNuevo: pacienteNuevo(pedido.paciente.datos) }),
    prestacionId: pedido.prestacionId,
    inicio: pedido.inicio,
    esSobreturno: pedido.esSobreturno,
  };
  const respuesta = await http.post<TurnoCreadoResponse | undefined>('/api/v1/turnos', cuerpo);
  return { turno: aDetalle(respuesta?.turno), pacienteReutilizado: respuesta?.pacienteReutilizado === true };
}

export type AccionDeEstado = CambiarEstadoRequest['accion'];

export async function cambiarEstado(http: ClienteHttp, id: string, pedido: { readonly accion: AccionDeEstado; readonly version: number; readonly motivo?: string }): Promise<DetalleTurno> {
  const motivo = pedido.motivo?.trim() ?? '';
  const cuerpo: CambiarEstadoRequest = { accion: pedido.accion, version: pedido.version, ...(pedido.accion === 'CANCELAR' && motivo !== '' ? { motivo } : {}) };
  return aDetalle(await http.patch<DetalleResponse | undefined>(`${rutaDelTurno(id)}/estado`, cuerpo));
}

/** Mueve el turno conservando su duración y el link del paciente (RN-AG-11). */
export async function reprogramarTurno(http: ClienteHttp, id: string, pedido: { readonly inicio: string; readonly version: number }): Promise<DetalleTurno> {
  return aDetalle(await http.post<DetalleResponse | undefined>(`${rutaDelTurno(id)}/reprogramar`, pedido));
}

export async function ajustarDuracion(http: ClienteHttp, id: string, pedido: { readonly duracionMin: number; readonly version: number }): Promise<DetalleTurno> {
  return aDetalle(await http.patch<DetalleResponse | undefined>(`${rutaDelTurno(id)}/duracion`, pedido));
}

/** Una nota vacía la borra (C-12). */
export async function editarNota(http: ClienteHttp, id: string, pedido: { readonly nota: string; readonly version: number }): Promise<DetalleTurno> {
  const nota = pedido.nota.trim();
  return aDetalle(await http.patch<DetalleResponse | undefined>(`${rutaDelTurno(id)}/nota`, { nota: nota === '' ? null : nota, version: pedido.version }));
}

/** Turnos futuros activos que quedaron fuera del horario de atención, sin sobreturnos (C-12, #34). */
export async function turnosFueraDeDisponibilidad(http: ClienteHttp): Promise<TurnoAgenda[]> {
  return sinNulos((await http.get<TurnoResponse[] | undefined>('/api/v1/turnos/fuera-de-disponibilidad') ?? []).map(aTurno));
}

export interface PrestacionActiva {
  readonly id: string;
  readonly nombre: string;
  readonly duracionMin: number;
}

/** Prestaciones activas de C-08, en el orden del consultorio. */
export async function obtenerPrestacionesActivas(http: ClienteHttp): Promise<PrestacionActiva[]> {
  return (await listarPrestaciones(http)).flatMap((p) =>
    p.activa === false || p.id === undefined || p.nombre === undefined || p.duracionMin === undefined ? [] : [{ id: p.id, nombre: p.nombre, duracionMin: p.duracionMin }],
  );
}

const PASO_POR_DEFECTO = 15;

/** Paso de huecos del consultorio (C-08) para sugerir horarios; si no se puede leer, el de por defecto. */
export async function obtenerPasoHuecos(http: ClienteHttp): Promise<number> {
  try {
    return (await obtenerConfiguracion(http)).pasoHuecosMin ?? PASO_POR_DEFECTO;
  } catch {
    return PASO_POR_DEFECTO;
  }
}
