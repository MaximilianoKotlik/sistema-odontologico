import { diaDeSemana, diaLocal, horaDeMinutos, horaLocal, minutosDeHora, minutosLocales, sumarDias } from './tiempo';

/**
 * Modelo de la agenda (C-13, design D1, D3 a D5): funciones puras que arman la línea de tiempo del día, los conteos,
 * el aviso de cambios de los pacientes y el rango de horas de la grilla semanal, siempre en la zona del consultorio.
 */

export type EstadoTurno = 'RESERVADO' | 'CONFIRMADO' | 'CANCELADO' | 'AUSENTE';
export type CambioPaciente = 'RESERVO' | 'REPROGRAMO' | 'CANCELO';

export interface Paciente {
  readonly nombre: string;
  readonly apellido: string;
}

export interface TurnoAgenda {
  readonly id: string;
  readonly inicio: string;
  readonly fin: string;
  readonly duracionMin: number;
  readonly estado: EstadoTurno;
  readonly esSobreturno: boolean;
  readonly origen: 'LINK' | 'MANUAL';
  readonly prestacion: string;
  readonly paciente: Paciente;
  readonly tieneNota: boolean;
  readonly canceladoPor: 'ODONTOLOGO' | 'PACIENTE' | null;
  readonly cambioPaciente: CambioPaciente | null;
  readonly novedadPacienteAt: string | null;
}

export interface BloqueoAgenda {
  readonly id: string;
  readonly inicio: string;
  readonly fin: string;
  readonly motivo: string | null;
}

export interface FranjaDisponible {
  readonly diaSemana: number;
  readonly horaInicio: string;
  readonly horaFin: string;
}

export type Fila =
  | { readonly tipo: 'turno'; readonly turno: TurnoAgenda; readonly desde: string; readonly hasta: string; readonly superponeCon?: { readonly hora: string; readonly paciente: string } }
  | { readonly tipo: 'bloqueo'; readonly bloqueo: BloqueoAgenda; readonly desde: string; readonly hasta: string }
  | { readonly tipo: 'libre'; readonly desde: string; readonly hasta: string }
  | { readonly tipo: 'fuera'; readonly desde: string; readonly hasta: string }
  | { readonly tipo: 'ahora'; readonly hora: string };

export const nombreCorto = (paciente: Paciente) => `${paciente.nombre} ${paciente.apellido.charAt(0)}.`;
export const nombreCompleto = (paciente: Paciente) => `${paciente.nombre} ${paciente.apellido}`;

export function turnosDelDia(dia: string, zona: string, turnos: readonly TurnoAgenda[]): TurnoAgenda[] {
  return turnos.filter((t) => diaLocal(t.inicio, zona) === dia);
}

const MINUTOS_DEL_DIA = 24 * 60;

interface Tramo {
  readonly desde: number;
  readonly hasta: number;
}

/** Tramo local del día que ocupa un intervalo (un bloqueo de varios días se recorta al día). */
function tramoEnElDia(inicio: string, fin: string, dia: string, zona: string): Tramo | null {
  const diaInicio = diaLocal(inicio, zona);
  const diaFin = diaLocal(fin, zona);
  if (diaInicio > dia || diaFin < dia) return null;
  const desde = diaInicio === dia ? minutosLocales(inicio, zona) : 0;
  const hasta = diaFin === dia ? minutosLocales(fin, zona) : MINUTOS_DEL_DIA;
  return hasta > desde ? { desde, hasta } : null;
}

function restar(tramo: Tramo, ocupados: readonly Tramo[]): Tramo[] {
  let libres: Tramo[] = [tramo];
  for (const ocupado of ocupados) {
    libres = libres.flatMap((libre) => {
      if (ocupado.hasta <= libre.desde || ocupado.desde >= libre.hasta) return [libre];
      return [
        ...(ocupado.desde > libre.desde ? [{ desde: libre.desde, hasta: ocupado.desde }] : []),
        ...(ocupado.hasta < libre.hasta ? [{ desde: ocupado.hasta, hasta: libre.hasta }] : []),
      ];
    });
  }
  return libres;
}

export interface EntradaDelDia {
  readonly dia: string;
  readonly zona: string;
  readonly turnos: readonly TurnoAgenda[];
  readonly bloqueos: readonly BloqueoAgenda[];
  readonly franjas: readonly FranjaDisponible[];
  readonly ahora: Date;
  readonly mostrarCancelados: boolean;
}

type FilaConTramo = { readonly fila: Fila; readonly tramo: Tramo };

/**
 * Línea de tiempo del día (D3): turnos, sobreturnos y bloqueos en orden; los huecos libres dentro del horario de
 * atención; los tramos sin atención entre franjas; y la marca de ahora si el día es hoy. Un cancelado visible ocupa
 * su lugar, como en el mockup aprobado; oculto, su hueco vuelve a ser libre.
 */
export function lineaDelDia(entrada: EntradaDelDia): Fila[] {
  const { dia, zona } = entrada;
  const visibles = turnosDelDia(dia, zona, entrada.turnos).filter((t) => entrada.mostrarCancelados || t.estado !== 'CANCELADO');

  const deTurnos: FilaConTramo[] = visibles.flatMap((turno) => {
    const tramo = tramoEnElDia(turno.inicio, turno.fin, dia, zona);
    return tramo === null ? [] : [{ tramo, fila: { tipo: 'turno', turno, desde: horaLocal(turno.inicio, zona), hasta: horaLocal(turno.fin, zona) } as Fila }];
  });
  const deBloqueos: FilaConTramo[] = entrada.bloqueos.flatMap((bloqueo) => {
    const tramo = tramoEnElDia(bloqueo.inicio, bloqueo.fin, dia, zona);
    return tramo === null ? [] : [{ tramo, fila: { tipo: 'bloqueo', bloqueo, desde: horaDeMinutos(tramo.desde), hasta: tramo.hasta === MINUTOS_DEL_DIA ? '24:00' : horaDeMinutos(tramo.hasta) } as Fila }];
  });

  const ocupados = [...deTurnos, ...deBloqueos].map((x) => x.tramo);
  const franjas = entrada.franjas
    .filter((f) => f.diaSemana === diaDeSemana(dia))
    .map((f) => ({ desde: minutosDeHora(f.horaInicio), hasta: minutosDeHora(f.horaFin) }))
    .sort((a, b) => a.desde - b.desde);

  const libres: FilaConTramo[] = franjas.flatMap((franja) =>
    restar(franja, ocupados).map((tramo) => ({ tramo, fila: { tipo: 'libre', desde: horaDeMinutos(tramo.desde), hasta: horaDeMinutos(tramo.hasta) } as Fila })),
  );
  const fuera: FilaConTramo[] = franjas.slice(1).flatMap((franja, i) => {
    const anterior = franjas[i];
    if (anterior === undefined || franja.desde <= anterior.hasta) return [];
    const tramo = { desde: anterior.hasta, hasta: franja.desde };
    return [{ tramo, fila: { tipo: 'fuera', desde: horaDeMinutos(tramo.desde), hasta: horaDeMinutos(tramo.hasta) } as Fila }];
  });

  // Turnos que no son sobreturno, bloqueos, libres y tramos fuera de horario, por hora de inicio.
  const base = [...deTurnos.filter((x) => !(x.fila.tipo === 'turno' && x.fila.turno.esSobreturno)), ...deBloqueos, ...libres, ...fuera].sort((a, b) => a.tramo.desde - b.tramo.desde);

  // Cada sobreturno va justo después del turno con el que se superpone (D3) o, si no se cruza con ninguno, por hora.
  for (const sobre of deTurnos.filter((x) => x.fila.tipo === 'turno' && x.fila.turno.esSobreturno)) {
    const indiceBase = base.findIndex((x) => x.fila.tipo === 'turno' && x.fila.turno.estado !== 'CANCELADO' && x.tramo.desde < sobre.tramo.hasta && sobre.tramo.desde < x.tramo.hasta);
    const conBase = base[indiceBase];
    if (conBase !== undefined && conBase.fila.tipo === 'turno' && sobre.fila.tipo === 'turno') {
      const superpuesto: FilaConTramo = { tramo: sobre.tramo, fila: { ...sobre.fila, superponeCon: { hora: conBase.fila.desde, paciente: nombreCorto(conBase.fila.turno.paciente) } } };
      let posicion = indiceBase + 1;
      while (base[posicion]?.fila.tipo === 'turno' && (base[posicion]?.fila as Extract<Fila, { tipo: 'turno' }>).turno.esSobreturno) posicion += 1;
      base.splice(posicion, 0, superpuesto);
    } else {
      const posicion = base.findIndex((x) => x.tramo.desde > sobre.tramo.desde);
      base.splice(posicion === -1 ? base.length : posicion, 0, sobre);
    }
  }

  const filas = base.map((x) => x.fila);
  if (diaLocal(entrada.ahora, zona) !== dia) return filas;

  // Ahora va antes del primer turno o bloqueo que todavía no empezó, o del primer libre o tramo que todavía no terminó.
  const ahora = minutosLocales(entrada.ahora, zona);
  const posicion = base.findIndex((x) => ((x.fila.tipo === 'turno' || x.fila.tipo === 'bloqueo') && x.tramo.desde > ahora) || ((x.fila.tipo === 'libre' || x.fila.tipo === 'fuera') && x.tramo.hasta > ahora));
  filas.splice(posicion === -1 ? filas.length : posicion, 0, { tipo: 'ahora', hora: horaDeMinutos(ahora) });
  return filas;
}

export interface Resumen {
  readonly turnos: number;
  readonly sobreturnos: number;
  readonly cancelados: number;
}

export function resumenDelDia(turnos: readonly TurnoAgenda[], mostrarCancelados: boolean): Resumen {
  const activos = turnos.filter((t) => t.estado !== 'CANCELADO');
  return {
    turnos: activos.length,
    sobreturnos: activos.filter((t) => t.esSobreturno).length,
    cancelados: mostrarCancelados ? turnos.length - activos.length : 0,
  };
}

export function conteoPorDia(lunes: string, zona: string, turnos: readonly TurnoAgenda[], mostrarCancelados: boolean): { readonly dia: string; readonly cantidad: number }[] {
  return Array.from({ length: 7 }, (_, i) => {
    const dia = sumarDias(lunes, i);
    return { dia, cantidad: turnosDelDia(dia, zona, turnos).filter((t) => mostrarCancelados || t.estado !== 'CANCELADO').length };
  });
}

const VERBO: Record<CambioPaciente, string> = { RESERVO: 'reservó', REPROGRAMO: 'reprogramó', CANCELO: 'canceló' };

/** Aviso de cambios de los pacientes sin ver (DD-30): "3 cambios de tus pacientes" y quién hizo qué. */
export function avisoDeNuevos(turnos: readonly TurnoAgenda[]): { readonly titulo: string; readonly detalle: string } | null {
  const conCambio = turnos.filter((t) => t.cambioPaciente !== null);
  if (conCambio.length === 0) return null;
  const frases = conCambio.map((t) => `${nombreCorto(t.paciente)} ${VERBO[t.cambioPaciente ?? 'RESERVO']}`);
  const ultima = frases.pop() ?? '';
  return {
    titulo: conCambio.length === 1 ? '1 cambio de tus pacientes' : `${conCambio.length} cambios de tus pacientes`,
    detalle: `${frases.length === 0 ? ultima : `${frases.join(', ')} y ${ultima}`}.`,
  };
}

/** Rango de horas de la grilla semanal (D4): 08:00 a 20:00, ampliado a la disponibilidad y los turnos, en horas enteras. */
export function rangoDeHoras(franjas: readonly FranjaDisponible[], turnos: readonly TurnoAgenda[], zona: string): Tramo {
  const inicios = [8 * 60, ...franjas.map((f) => minutosDeHora(f.horaInicio)), ...turnos.map((t) => minutosLocales(t.inicio, zona))];
  const fines = [20 * 60, ...franjas.map((f) => minutosDeHora(f.horaFin)), ...turnos.map((t) => minutosLocales(t.fin, zona))];
  return { desde: Math.floor(Math.min(...inicios) / 60) * 60, hasta: Math.min(MINUTOS_DEL_DIA, Math.ceil(Math.max(...fines) / 60) * 60) };
}
