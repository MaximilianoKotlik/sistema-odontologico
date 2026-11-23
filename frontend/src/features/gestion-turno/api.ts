import type { components } from '../../shared/api-types';

/**
 * `VistaDeTurno` de C-15 (`GET /api/v1/public/turnos/{token}`), con type guard. Solo se toman los campos que la página
 * muestra: si la API agregara datos privados, no llegan a la pantalla (spec pantalla-gestion-turno).
 */
type Contrato = components['schemas']['VistaDeTurno'];

export interface VistaDeTurno {
  readonly turno: {
    readonly inicio: string;
    readonly fin: string;
    readonly prestacion: string;
    readonly estado: string;
    readonly canceladoPor: 'ODONTOLOGO' | 'PACIENTE' | null;
  };
  readonly paciente: { readonly nombre: string; readonly apellido: string };
  readonly consultorio: { readonly nombre: string; readonly direccion: string | null; readonly telefono: string | null; readonly zonaHoraria: string };
  readonly puedeModificar: boolean;
  readonly puedeReprogramar: boolean;
  readonly modificableHasta: string | null;
}

const esObjeto = (v: unknown): v is Record<string, unknown> => typeof v === 'object' && v !== null && !Array.isArray(v);
const esTexto = (v: unknown): v is string => typeof v === 'string' && v !== '';
const opcional = (v: unknown): string | null => (esTexto(v) ? v : null);

export function aVistaDeTurno(v: unknown): VistaDeTurno | null {
  if (!esObjeto(v)) return null;
  const { turno, paciente, consultorio } = v as Record<keyof Contrato, unknown>;
  if (!esObjeto(turno) || !esObjeto(paciente) || !esObjeto(consultorio)) return null;
  if (!esTexto(turno['inicio']) || !esTexto(turno['fin']) || !esTexto(turno['prestacion']) || !esTexto(turno['estado'])) return null;
  if (!esTexto(paciente['nombre']) || !esTexto(consultorio['nombre']) || !esTexto(consultorio['zonaHoraria'])) return null;
  const canceladoPor = turno['canceladoPor'];
  return {
    turno: {
      inicio: turno['inicio'],
      fin: turno['fin'],
      prestacion: turno['prestacion'],
      estado: turno['estado'],
      canceladoPor: canceladoPor === 'ODONTOLOGO' || canceladoPor === 'PACIENTE' ? canceladoPor : null,
    },
    paciente: { nombre: paciente['nombre'], apellido: typeof paciente['apellido'] === 'string' ? paciente['apellido'] : '' },
    consultorio: {
      nombre: consultorio['nombre'],
      direccion: opcional(consultorio['direccion']),
      telefono: opcional(consultorio['telefono']),
      zonaHoraria: consultorio['zonaHoraria'],
    },
    puedeModificar: v['puedeModificar'] === true,
    puedeReprogramar: v['puedeReprogramar'] === true,
    modificableHasta: opcional(v['modificableHasta']),
  };
}
