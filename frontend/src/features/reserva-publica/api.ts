import type { components } from '../../shared/api-types';
import type { DiaDeHuecos } from './dias';

/**
 * Contrato de la API pública de C-15 (`api-types.ts`), con type guards: las respuestas llegan como `unknown` y una
 * que no cumple el contrato se trata como error, nunca se muestra a medias.
 */
type Esquemas = components['schemas'];

export interface Prestacion {
  readonly id: string;
  readonly nombre: string;
  readonly duracionMin: number;
}

export interface Consultorio {
  readonly nombre: string;
  readonly direccion: string | null;
  readonly telefono: string | null;
  readonly zonaHoraria: string;
  readonly tomaTurnosOnline: boolean;
  /** Último día reservable, en fecha local del consultorio. Vacío si no toma turnos online. */
  readonly reservableHasta: string;
  readonly prestaciones: readonly Prestacion[];
}

export interface Huecos {
  readonly zonaHoraria: string;
  readonly dias: readonly DiaDeHuecos[];
}

export interface ReservaCreada {
  readonly turno: { readonly id: string; readonly inicio: string; readonly fin: string };
  readonly linkGestion: string;
}

/** Cuerpo de `POST /api/v1/public/consultorios/{slug}/turnos`. */
export type PedidoDeReserva = Esquemas['PedidoDeReservaDto'];

const esObjeto = (v: unknown): v is Record<string, unknown> => typeof v === 'object' && v !== null && !Array.isArray(v);
const esTexto = (v: unknown): v is string => typeof v === 'string' && v !== '';
const textoOpcional = (v: unknown): string | null => (esTexto(v) ? v : null);
const FECHA = /^\d{4}-\d{2}-\d{2}$/;

function aPrestacion(v: unknown): Prestacion | null {
  if (!esObjeto(v) || !esTexto(v['id']) || !esTexto(v['nombre']) || typeof v['duracionMin'] !== 'number') return null;
  return { id: v['id'], nombre: v['nombre'], duracionMin: v['duracionMin'] };
}

export function aConsultorio(v: unknown): Consultorio | null {
  if (!esObjeto(v) || !esTexto(v['nombre']) || !esTexto(v['zonaHoraria']) || typeof v['tomaTurnosOnline'] !== 'boolean') return null;
  const toma = v['tomaTurnosOnline'];
  const reservableHasta = v['reservableHasta'];
  if (toma && !(typeof reservableHasta === 'string' && FECHA.test(reservableHasta))) return null;
  const crudas = Array.isArray(v['prestaciones']) ? v['prestaciones'] : [];
  const prestaciones = crudas.map(aPrestacion);
  if (prestaciones.some((p) => p === null)) return null;
  return {
    nombre: v['nombre'],
    direccion: textoOpcional(v['direccion']),
    telefono: textoOpcional(v['telefono']),
    zonaHoraria: v['zonaHoraria'],
    tomaTurnosOnline: toma,
    reservableHasta: typeof reservableHasta === 'string' ? reservableHasta : '',
    prestaciones: prestaciones.filter((p): p is Prestacion => p !== null),
  };
}

function aDia(v: unknown): DiaDeHuecos | null {
  if (!esObjeto(v) || !(typeof v['fecha'] === 'string' && FECHA.test(v['fecha'])) || typeof v['atiende'] !== 'boolean') return null;
  const huecos = Array.isArray(v['huecos']) ? v['huecos'] : [];
  if (!huecos.every(esTexto)) return null;
  return { fecha: v['fecha'], atiende: v['atiende'], huecos };
}

export function aHuecos(v: unknown): Huecos | null {
  if (!esObjeto(v) || !esTexto(v['zonaHoraria']) || !Array.isArray(v['dias'])) return null;
  const dias = v['dias'].map(aDia);
  if (dias.some((d) => d === null)) return null;
  return { zonaHoraria: v['zonaHoraria'], dias: dias.filter((d): d is DiaDeHuecos => d !== null) };
}

export function aReservaCreada(v: unknown): ReservaCreada | null {
  if (!esObjeto(v) || !esTexto(v['linkGestion']) || !esObjeto(v['turno'])) return null;
  const turno = v['turno'];
  if (!esTexto(turno['id']) || !esTexto(turno['inicio']) || !esTexto(turno['fin'])) return null;
  return { turno: { id: turno['id'], inicio: turno['inicio'], fin: turno['fin'] }, linkGestion: v['linkGestion'] };
}
