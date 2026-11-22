import { formatearDia } from '../../shared/fechas';
import type { BloqueoAgenda } from '../agenda/modelo';
import { diaLocal, horaLocal, instanteLocal, lunesDe, sumarDias } from '../agenda/tiempo';
import { validarHora, type ErrorDeCampo } from '../turnos/validaciones';

/**
 * Lógica pura de los bloqueos (C-14, design D8; spec bloqueos): el rango por horas o por días completos en la zona del
 * consultorio, el motivo, la descripción de cada bloqueo y su agrupación en el listado.
 */

export const LARGO_MOTIVO_BLOQUEO = 120;

export interface FormularioBloqueo {
  readonly todoElDia: boolean;
  /** Por horas: el día y las horas desde y hasta. */
  readonly dia: string;
  readonly desde: string;
  readonly hasta: string;
  /** Días completos: del primer al último día, incluidos. */
  readonly diaDesde: string;
  readonly diaHasta: string;
}

export type CampoBloqueo = 'dia' | 'desde' | 'hasta' | 'diaDesde' | 'diaHasta' | 'motivo';

export type RangoValidado = { readonly ok: true; readonly inicio: string; readonly fin: string } | { readonly ok: false; readonly errores: ErrorDeCampo<CampoBloqueo>[] };

const DIA = /^\d{4}-\d{2}-\d{2}$/;

const errorDeHora = (hora: string, vacia: string) => (hora === '' ? vacia : validarHora(hora));

export function rangoDelBloqueo(formulario: FormularioBloqueo, zona: string): RangoValidado {
  const errores: ErrorDeCampo<CampoBloqueo>[] = [];
  if (formulario.todoElDia) {
    if (!DIA.test(formulario.diaDesde)) errores.push({ campo: 'diaDesde', mensaje: 'Elegí el primer día.' });
    if (!DIA.test(formulario.diaHasta)) errores.push({ campo: 'diaHasta', mensaje: 'Elegí el último día.' });
    else if (DIA.test(formulario.diaDesde) && formulario.diaHasta < formulario.diaDesde) errores.push({ campo: 'diaHasta', mensaje: 'Tiene que ser el mismo día o uno posterior al primero.' });
    if (errores.length > 0) return { ok: false, errores };
    return {
      ok: true,
      inicio: instanteLocal(formulario.diaDesde, '00:00', zona).toISOString(),
      fin: instanteLocal(sumarDias(formulario.diaHasta, 1), '00:00', zona).toISOString(),
    };
  }
  if (!DIA.test(formulario.dia)) errores.push({ campo: 'dia', mensaje: 'Elegí el día.' });
  const errorDesde = errorDeHora(formulario.desde, 'Elegí desde qué hora.');
  if (errorDesde !== null) errores.push({ campo: 'desde', mensaje: errorDesde });
  const errorHasta = errorDeHora(formulario.hasta, 'Elegí hasta qué hora.');
  if (errorHasta !== null) errores.push({ campo: 'hasta', mensaje: errorHasta });
  else if (errorDesde === null && formulario.hasta <= formulario.desde) errores.push({ campo: 'hasta', mensaje: `Tiene que terminar después de las ${formulario.desde}.` });
  if (errores.length > 0) return { ok: false, errores };
  return { ok: true, inicio: instanteLocal(formulario.dia, formulario.desde, zona).toISOString(), fin: instanteLocal(formulario.dia, formulario.hasta, zona).toISOString() };
}

export const validarMotivoBloqueo = (motivo: string) => (motivo.trim().length > LARGO_MOTIVO_BLOQUEO ? 'El motivo puede tener hasta 120 caracteres.' : null);

const mayuscula = (texto: string) => texto.charAt(0).toUpperCase() + texto.slice(1);

/** "Viernes 20 de noviembre · 15:00 a 16:00", "Lunes 4 de enero a viernes 15 de enero · todo el día"… */
export function describirBloqueo(bloqueo: BloqueoAgenda, zona: string): string {
  const diaInicio = diaLocal(bloqueo.inicio, zona);
  const horaInicio = horaLocal(bloqueo.inicio, zona);
  const horaFin = horaLocal(bloqueo.fin, zona);
  if (horaInicio === '00:00' && horaFin === '00:00') {
    const ultimo = sumarDias(diaLocal(bloqueo.fin, zona), -1);
    const dias = ultimo === diaInicio ? formatearDia(diaInicio, zona) : `${formatearDia(diaInicio, zona)} a ${formatearDia(ultimo, zona)}`;
    return `${mayuscula(dias)} · todo el día`;
  }
  const diaFin = diaLocal(bloqueo.fin, zona);
  if (diaFin === diaInicio) return `${mayuscula(formatearDia(diaInicio, zona))} · ${horaInicio} a ${horaFin}`;
  return `${mayuscula(formatearDia(diaInicio, zona))} ${horaInicio} a ${formatearDia(diaFin, zona)} ${horaFin}`;
}

export interface GrupoDeBloqueos {
  readonly titulo: string;
  readonly bloqueos: BloqueoAgenda[];
}

/** "Esta semana" (empiezan antes del lunes que viene, incluidos los que están en curso) y "Más adelante". */
export function agruparBloqueos(bloqueos: readonly BloqueoAgenda[], ahora: Date, zona: string): GrupoDeBloqueos[] {
  const proximoLunes = instanteLocal(sumarDias(lunesDe(diaLocal(ahora, zona)), 7), '00:00', zona).getTime();
  const ordenados = [...bloqueos].sort((a, b) => Date.parse(a.inicio) - Date.parse(b.inicio));
  const grupos: GrupoDeBloqueos[] = [
    { titulo: 'Esta semana', bloqueos: ordenados.filter((b) => Date.parse(b.inicio) < proximoLunes) },
    { titulo: 'Más adelante', bloqueos: ordenados.filter((b) => Date.parse(b.inicio) >= proximoLunes) },
  ];
  return grupos.filter((g) => g.bloqueos.length > 0);
}
