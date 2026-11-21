/**
 * Calendario y horas en la zona del consultorio (C-13, RN-TE-05). Los días son `YYYY-MM-DD` y las horas `HH:mm`
 * locales; los instantes van y vienen en UTC. Nunca se usa la zona del dispositivo.
 */

import type { Instante } from '../../shared/fechas';

const formateadores = new Map<string, Intl.DateTimeFormat>();

function partesEn(zona: string, fecha: Date): Record<string, number> {
  let formato = formateadores.get(zona);
  if (formato === undefined) {
    formato = new Intl.DateTimeFormat('en-US', { timeZone: zona, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23' });
    formateadores.set(zona, formato);
  }
  const resultado: Record<string, number> = {};
  for (const parte of formato.formatToParts(fecha)) if (parte.type !== 'literal') resultado[parte.type] = Number(parte.value);
  return resultado;
}

const aDate = (instante: Instante) => (instante instanceof Date ? instante : new Date(instante));

/** Diferencia en minutos entre la hora local de la zona y UTC en ese instante. */
function desfase(zona: string, fecha: Date): number {
  const p = partesEn(zona, fecha);
  const comoUtc = Date.UTC(p['year'] ?? 0, (p['month'] ?? 1) - 1, p['day'] ?? 1, p['hour'] ?? 0, p['minute'] ?? 0, p['second'] ?? 0);
  return Math.round((comoUtc - fecha.getTime()) / 60000);
}

/** Instante UTC de un día y una hora locales de la zona (se corrige dos veces por si el desfase cambia ese día). */
export function instanteLocal(dia: string, hora: string, zona: string): Date {
  const [anio, mes, d] = dia.split('-').map(Number);
  const [h, m] = hora.split(':').map(Number);
  const ingenuo = Date.UTC(anio ?? 0, (mes ?? 1) - 1, d ?? 1, h ?? 0, m ?? 0);
  let instante = new Date(ingenuo - desfase(zona, new Date(ingenuo)) * 60000);
  instante = new Date(ingenuo - desfase(zona, instante) * 60000);
  return instante;
}

const dosCifras = (n: number) => String(n).padStart(2, '0');

export function diaLocal(instante: Instante, zona: string): string {
  const p = partesEn(zona, aDate(instante));
  return `${p['year'] ?? ''}-${dosCifras(p['month'] ?? 0)}-${dosCifras(p['day'] ?? 0)}`;
}

/** "09:00": con cero inicial, como en los mockups de la agenda. */
export function horaLocal(instante: Instante, zona: string): string {
  const p = partesEn(zona, aDate(instante));
  return `${dosCifras(p['hour'] ?? 0)}:${dosCifras(p['minute'] ?? 0)}`;
}

export function minutosLocales(instante: Instante, zona: string): number {
  const p = partesEn(zona, aDate(instante));
  return (p['hour'] ?? 0) * 60 + (p['minute'] ?? 0);
}

const comoFechaUtc = (dia: string) => new Date(`${dia}T12:00:00Z`);

/** 1 = lunes … 7 = domingo. */
export function diaDeSemana(dia: string): number {
  const domingoCero = comoFechaUtc(dia).getUTCDay();
  return domingoCero === 0 ? 7 : domingoCero;
}

export function sumarDias(dia: string, cantidad: number): string {
  const fecha = comoFechaUtc(dia);
  fecha.setUTCDate(fecha.getUTCDate() + cantidad);
  return fecha.toISOString().slice(0, 10);
}

export const lunesDe = (dia: string) => sumarDias(dia, 1 - diaDeSemana(dia));

export const minutosDeHora = (hora: string) => {
  const [h, m] = hora.split(':').map(Number);
  return (h ?? 0) * 60 + (m ?? 0);
};

export const horaDeMinutos = (minutos: number) => `${dosCifras(Math.floor(minutos / 60))}:${dosCifras(minutos % 60)}`;
