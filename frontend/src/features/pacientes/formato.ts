import { hoyEn } from '../../shared/fechas';

/** Textos de las pantallas de pacientes (C-17, mockup aprobado #36). Fechas en la zona del consultorio. */

const diaYMes = new Map<string, Intl.DateTimeFormat>();

function diaDelMes(instante: string, zona: string): string {
  let formato = diaYMes.get(zona);
  if (formato === undefined) {
    formato = new Intl.DateTimeFormat('es-AR', { timeZone: zona, day: 'numeric', month: 'long' });
    diaYMes.set(zona, formato);
  }
  return formato.format(new Date(instante));
}

/** "hoy" o "el 18 de noviembre". */
export function diaDeLaReserva(instante: string, zona: string, ahora: Date = new Date()): string {
  return hoyEn(zona, new Date(instante)) === hoyEn(zona, ahora) ? 'hoy' : `el ${diaDelMes(instante, zona)}`;
}

export function lineaDeLoInformado(informado: { readonly nombre: string; readonly apellido: string; readonly informadoAt: string }, zona: string, ahora: Date = new Date()): string {
  return `Escribió «${informado.nombre} ${informado.apellido}» al reservar ${diaDeLaReserva(informado.informadoAt, zona, ahora)}`;
}

export const nombreDeLista = (p: { readonly nombre: string; readonly apellido: string }) => `${p.apellido}, ${p.nombre}`;
export const nombreCompleto = (p: { readonly nombre: string; readonly apellido: string }) => `${p.nombre} ${p.apellido}`;

export const cantidadConDatos = (n: number) => `${n} ${n === 1 ? 'paciente' : 'pacientes'} con datos para revisar`;
