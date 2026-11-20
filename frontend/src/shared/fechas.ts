/**
 * Presentación de fechas en es-AR y en la zona horaria del consultorio (spec fechas-zona-horaria, design D7).
 * Nunca se usa la zona del dispositivo (`Date#getHours()` y similares están prohibidos para mostrar datos).
 */

/** Instante UTC tal como lo devuelve la API (ISO 8601) o un `Date`. */
export type Instante = string | Date;

const DIA_CALENDARIO = /^(\d{4})-(\d{2})-(\d{2})$/;
const formateadores = new Map<string, Intl.DateTimeFormat>();

function formateador(clave: string, opciones: Intl.DateTimeFormatOptions): Intl.DateTimeFormat {
  const existente = formateadores.get(clave);
  if (existente !== undefined) return existente;
  let nuevo: Intl.DateTimeFormat;
  try {
    nuevo = new Intl.DateTimeFormat('es-AR', opciones);
  } catch {
    throw new RangeError(`Zona horaria inválida: ${String(opciones.timeZone)}`);
  }
  formateadores.set(clave, nuevo);
  return nuevo;
}

function partes(formato: Intl.DateTimeFormat, fecha: Date): Map<Intl.DateTimeFormatPartTypes, string> {
  return new Map(formato.formatToParts(fecha).map((p) => [p.type, p.value]));
}

function aDate(instante: Instante): Date {
  return instante instanceof Date ? instante : new Date(instante);
}

/** Hora en 24 h sin cero inicial, p. ej. "9:00" o "18:05". */
export function formatearHora(instante: Instante, zona: string): string {
  const formato = formateador(`hora|${zona}`, { timeZone: zona, hour: '2-digit', minute: '2-digit', hourCycle: 'h23' });
  const p = partes(formato, aDate(instante));
  return `${Number(p.get('hour'))}:${p.get('minute') ?? '00'}`;
}

/**
 * Día para encabezados, p. ej. "martes 24 de noviembre".
 * Un día calendario (`YYYY-MM-DD`) se muestra tal cual; un instante se convierte a la zona del consultorio.
 */
export function formatearDia(instanteODia: Instante, zona: string): string {
  const esDiaCalendario = typeof instanteODia === 'string' && DIA_CALENDARIO.test(instanteODia);
  const zonaEfectiva = esDiaCalendario ? 'UTC' : zona;
  const fecha = esDiaCalendario ? new Date(`${instanteODia}T12:00:00Z`) : aDate(instanteODia);
  const formato = formateador(`dia|${zonaEfectiva}`, { timeZone: zonaEfectiva, weekday: 'long', day: 'numeric', month: 'long' });
  const p = partes(formato, fecha);
  return `${p.get('weekday') ?? ''} ${p.get('day') ?? ''} de ${p.get('month') ?? ''}`;
}

/** Día calendario actual (`YYYY-MM-DD`) en la zona del consultorio. */
export function hoyEn(zona: string, ahora: Date = new Date()): string {
  const formato = formateador(`iso|${zona}`, { timeZone: zona, year: 'numeric', month: '2-digit', day: '2-digit' });
  const p = partes(formato, ahora);
  return `${p.get('year') ?? ''}-${p.get('month') ?? ''}-${p.get('day') ?? ''}`;
}
