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

/** Turnos visibles con un cambio del paciente sin ver (DD-30): los que llevan la etiqueta "Nuevo" y cuenta el aviso. */
export function nuevosVisibles(turnos: readonly TurnoAgenda[], mostrarCancelados: boolean): TurnoAgenda[] {
  return turnos.filter((t) => t.cambioPaciente !== null && (mostrarCancelados || t.estado !== 'CANCELADO'));
}

/** Tramos atendidos del día, en minutos locales y en orden. */
export function franjasDelDia(dia: string, franjas: readonly FranjaDisponible[]): Tramo[] {
  return franjas
    .filter((f) => f.diaSemana === diaDeSemana(dia))
    .map((f) => ({ desde: minutosDeHora(f.horaInicio), hasta: minutosDeHora(f.horaFin) }))
    .sort((a, b) => a.desde - b.desde);
}

export type BloqueDeGrilla =
  | { readonly tipo: 'turno'; readonly turno: TurnoAgenda; readonly desde: number; readonly hasta: number; readonly superpuesto: boolean }
  | { readonly tipo: 'bloqueo'; readonly bloqueo: BloqueoAgenda; readonly desde: number; readonly hasta: number; readonly superpuesto: false };

/**
 * Bloques de un día de la grilla semanal (D4), en minutos locales. Con un solo sillón (DD-36) lo único que se
 * superpone es un sobreturno: el turno base ocupa todo el ancho y el sobreturno que se cruza con él va a la derecha.
 */
export function bloquesDelDia(entrada: Omit<EntradaDelDia, 'franjas' | 'ahora'>): BloqueDeGrilla[] {
  const { dia, zona } = entrada;
  const turnos = entrada.turnos
    .filter((t) => entrada.mostrarCancelados || t.estado !== 'CANCELADO')
    .flatMap((turno) => {
      const tramo = tramoEnElDia(turno.inicio, turno.fin, dia, zona);
      return tramo === null ? [] : [{ turno, ...tramo }];
    });
  const ocupanSillon = turnos.filter((t) => !t.turno.esSobreturno && t.turno.estado !== 'CANCELADO');
  const deTurnos: BloqueDeGrilla[] = turnos.map((t) => ({
    tipo: 'turno',
    turno: t.turno,
    desde: t.desde,
    hasta: t.hasta,
    superpuesto: t.turno.esSobreturno && ocupanSillon.some((o) => o.desde < t.hasta && t.desde < o.hasta),
  }));
  const deBloqueos: BloqueDeGrilla[] = entrada.bloqueos.flatMap((bloqueo) => {
    const tramo = tramoEnElDia(bloqueo.inicio, bloqueo.fin, dia, zona);
    return tramo === null ? [] : [{ tipo: 'bloqueo' as const, bloqueo, ...tramo, superpuesto: false as const }];
  });
  return [...deTurnos, ...deBloqueos].sort((a, b) => a.desde - b.desde || Number(a.superpuesto) - Number(b.superpuesto));
}

export type AccionTurno = 'CONFIRMAR' | 'REPROGRAMAR' | 'AJUSTAR_DURACION' | 'MARCAR_AUSENTE' | 'EDITAR_NOTA' | 'CANCELAR';

/** Acciones válidas según el estado (spec detalle-turno, US-023): "Marcar ausente" recién cuando el turno empezó. */
export function accionesDelTurno(estado: EstadoTurno, inicio: string, ahora: Date): AccionTurno[] {
  if (estado === 'CANCELADO' || estado === 'AUSENTE') return ['EDITAR_NOTA'];
  const empezo = Date.parse(inicio) <= ahora.getTime();
  return [...(estado === 'RESERVADO' ? (['CONFIRMAR'] as const) : []), 'REPROGRAMAR', 'AJUSTAR_DURACION', ...(empezo ? (['MARCAR_AUSENTE'] as const) : []), 'EDITAR_NOTA', 'CANCELAR'];
}

export type Vista = 'dia' | 'semana';

const FECHA_ISO = /^\d{4}-\d{2}-\d{2}$/;
const esFechaValida = (valor: string | null): valor is string =>
  valor !== null && FECHA_ISO.test(valor) && !Number.isNaN(Date.parse(`${valor}T12:00:00Z`)) && sumarDias(valor, 0) === valor;

/** Vista y fecha de la agenda desde la URL (D6). Sin parámetros: día de hoy en mobile y semana actual desde 768 px. */
export function vistaDeLaUrl(query: URLSearchParams, esAncho: boolean, hoy: string): { readonly vista: Vista; readonly fecha: string } {
  const pedida = query.get('vista');
  const vista: Vista = pedida === 'dia' || pedida === 'semana' ? pedida : esAncho ? 'semana' : 'dia';
  const fecha = query.get('fecha');
  const dia = esFechaValida(fecha) ? fecha : hoy;
  return { vista, fecha: vista === 'semana' ? sumarDias(dia, 1 - diaDeSemana(dia)) : dia };
}
