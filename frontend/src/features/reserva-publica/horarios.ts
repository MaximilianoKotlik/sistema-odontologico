import { formatearDia, formatearHora } from '../../shared/fechas';

/**
 * Horarios en la zona del consultorio (C-16, design D4). El instante se guarda tal como lo devolvió la API y se envía
 * igual: nunca se reconstruye desde la hora mostrada.
 */
export interface Horario {
  readonly instante: string;
  readonly hora: string;
}

export interface HorariosAgrupados {
  readonly manana: readonly Horario[];
  readonly tarde: readonly Horario[];
}

/** Desde las 13 en hora del consultorio, el horario va a "Tarde". */
const HORA_DE_LA_TARDE = 13;

export function agruparHorarios(huecos: readonly string[], zona: string): HorariosAgrupados {
  const manana: Horario[] = [];
  const tarde: Horario[] = [];
  for (const instante of huecos) {
    const hora = formatearHora(instante, zona);
    (Number(hora.split(':')[0]) < HORA_DE_LA_TARDE ? manana : tarde).push({ instante, hora });
  }
  return { manana, tarde };
}

const sinDeMes = (diaLargo: string) => diaLargo.replace(/ de .*$/, '');
const mayuscula = (texto: string) => texto.charAt(0).toLocaleUpperCase('es-AR') + texto.slice(1);

/** "martes 24, 10:30", para el botón "Continuar con …". */
export function cuandoCorto(instante: string, zona: string): string {
  return `${sinDeMes(formatearDia(instante, zona))}, ${formatearHora(instante, zona)}`;
}

/** "Martes 24 de noviembre, 10:30", para el resumen del turno. */
export function cuandoLargo(instante: string, zona: string): string {
  return `${mayuscula(formatearDia(instante, zona))}, ${formatearHora(instante, zona)}`;
}
