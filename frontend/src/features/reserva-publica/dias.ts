/**
 * Días de la tira de reserva (C-16, design D3). Las fechas son días calendario del consultorio (`YYYY-MM-DD`) y se
 * calculan en UTC, sin pasar por la zona del dispositivo.
 */

export interface DiaDeHuecos {
  readonly fecha: string;
  readonly atiende: boolean;
  /** Inicios ofrecibles, como instantes UTC de la API. */
  readonly huecos: readonly string[];
}

export interface Ventana {
  readonly desde: string;
  readonly hasta: string;
}

/** La tira pide de a 14 días: la mayoría reserva en la primera semana y la API acepta hasta 31. */
export const DIAS_POR_VENTANA = 14;

const aFecha = (dia: string) => new Date(`${dia}T12:00:00Z`);

export function sumarDias(dia: string, cantidad: number): string {
  const fecha = aFecha(dia);
  fecha.setUTCDate(fecha.getUTCDate() + cantidad);
  return fecha.toISOString().slice(0, 10);
}

/** Ventana que empieza en `desde`, cortada en el horizonte de reserva; null si ya se pasó. */
export function ventanaDesde(desde: string, reservableHasta: string): Ventana | null {
  if (desde > reservableHasta) return null;
  const fin = sumarDias(desde, DIAS_POR_VENTANA - 1);
  return { desde, hasta: fin < reservableHasta ? fin : reservableHasta };
}

/** "Ver más días" se ofrece hasta llegar al horizonte, aunque los días mostrados no tengan huecos. */
export function quedanDias(ultimoMostrado: string, reservableHasta: string): boolean {
  return ultimoMostrado < reservableHasta;
}

export function diaElegible(dia: DiaDeHuecos): boolean {
  return dia.atiende && dia.huecos.length > 0;
}

export function proximoDiaConHorarios(dias: readonly DiaDeHuecos[], despuesDe: string): DiaDeHuecos | null {
  return dias.find((d) => d.fecha > despuesDe && diaElegible(d)) ?? null;
}

export interface EtiquetaDia {
  /** "sáb" */
  readonly semana: string;
  /** "21" */
  readonly numero: string;
  /** "noviembre 2026" */
  readonly mes: string;
  /** "sábado 21" */
  readonly largo: string;
}

const corto = new Intl.DateTimeFormat('es-AR', { timeZone: 'UTC', weekday: 'short' });
const largo = new Intl.DateTimeFormat('es-AR', { timeZone: 'UTC', weekday: 'long' });
const mes = new Intl.DateTimeFormat('es-AR', { timeZone: 'UTC', month: 'long' });

export function etiquetaDia(dia: string): EtiquetaDia {
  const fecha = aFecha(dia);
  const numero = String(fecha.getUTCDate());
  return {
    semana: corto.format(fecha).replace('.', ''),
    numero,
    mes: `${mes.format(fecha)} ${fecha.getUTCFullYear()}`,
    largo: `${largo.format(fecha)} ${numero}`,
  };
}
