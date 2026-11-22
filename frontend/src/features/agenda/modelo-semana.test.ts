import { describe, expect, it } from 'vitest';
import { accionesDelTurno, bloquesDelDia, franjasDelDia, nuevosVisibles, vistaDeLaUrl, type TurnoAgenda } from './modelo';
import { instanteLocal } from './tiempo';

// Tareas 2.2 y 2.3 de C-13 (design D4, D6, spec detalle-turno). Datos ficticios. Vitest corre con TZ=Europe/Madrid.
const BA = 'America/Argentina/Buenos_Aires';
const DIA = '2026-11-20';
const en = (hora: string, dia = DIA) => instanteLocal(dia, hora, BA).toISOString();

function turno(id: string, desde: string, hasta: string, extra: Partial<TurnoAgenda> = {}): TurnoAgenda {
  return {
    id,
    inicio: en(desde),
    fin: en(hasta),
    duracionMin: 30,
    estado: 'CONFIRMADO',
    esSobreturno: false,
    origen: 'MANUAL',
    prestacion: 'Consulta',
    paciente: { nombre: 'Ana', apellido: 'Ejemplo' },
    tieneNota: false,
    canceladoPor: null,
    cambioPaciente: null,
    novedadPacienteAt: null,
    ...extra,
  };
}

const bloques = (turnos: TurnoAgenda[], extra: { mostrarCancelados?: boolean; bloqueos?: { id: string; inicio: string; fin: string; motivo: string | null }[] } = {}) =>
  bloquesDelDia({ dia: DIA, zona: BA, turnos, bloqueos: extra.bloqueos ?? [], mostrarCancelados: extra.mostrarCancelados ?? true }).map(
    (b) => `${b.tipo === 'turno' ? b.turno.id : b.bloqueo.id} ${b.desde}-${b.hasta}${b.superpuesto ? ' derecha' : ''}`,
  );

describe('bloquesDelDia (grilla semanal, D4)', () => {
  it('ubica turnos y bloqueos en minutos locales del día', () => {
    expect(bloques([turno('a', '09:00', '09:30')], { bloqueos: [{ id: 'b', inicio: en('15:00'), fin: en('16:00'), motivo: null }] })).toEqual(['a 540-570', 'b 900-960']);
  });

  it('el sobreturno que se cruza con un turno va a la derecha; el turno base ocupa todo el ancho', () => {
    expect(bloques([turno('base', '10:30', '11:30'), turno('sobre', '10:45', '11:05', { esSobreturno: true })])).toEqual(['base 630-690', 'sobre 645-665 derecha']);
  });

  it('un sobreturno que no se cruza con nadie ocupa todo el ancho', () => {
    expect(bloques([turno('sobre', '12:00', '12:20', { esSobreturno: true })])).toEqual(['sobre 720-740']);
  });

  it('un sobreturno sobre un cancelado no se corre: el cancelado ya no ocupa el sillón', () => {
    expect(bloques([turno('c', '10:30', '11:30', { estado: 'CANCELADO' }), turno('sobre', '10:45', '11:05', { esSobreturno: true })])).toEqual(['c 630-690', 'sobre 645-665']);
  });

  it('sin cancelados, los saca de la grilla', () => {
    expect(bloques([turno('c', '10:30', '11:30', { estado: 'CANCELADO' }), turno('a', '12:00', '12:30')], { mostrarCancelados: false })).toEqual(['a 720-750']);
  });

  it('recorta al día un bloqueo de varios días y omite lo que es de otro día', () => {
    expect(bloques([turno('otro', '09:00', '09:30', { inicio: en('09:00', '2026-11-21'), fin: en('09:30', '2026-11-21') })], { bloqueos: [{ id: 'b', inicio: en('18:00', '2026-11-19'), fin: en('10:00') , motivo: 'Congreso' }] })).toEqual(['b 0-600']);
  });
});

describe('franjasDelDia', () => {
  it('devuelve los tramos atendidos del día de la semana en minutos, ordenados', () => {
    const franjas = [
      { diaSemana: 5, horaInicio: '15:00', horaFin: '19:00' },
      { diaSemana: 5, horaInicio: '09:00', horaFin: '13:00' },
      { diaSemana: 6, horaInicio: '09:00', horaFin: '13:00' },
    ];
    expect(franjasDelDia(DIA, franjas)).toEqual([{ desde: 540, hasta: 780 }, { desde: 900, hasta: 1140 }]);
    expect(franjasDelDia('2026-11-22', franjas)).toEqual([]);
  });
});

describe('nuevosVisibles (2.3)', () => {
  it('cuenta solo los cambios sin ver de los turnos visibles', () => {
    const turnos = [turno('a', '09:00', '09:30', { cambioPaciente: 'RESERVO' }), turno('c', '10:00', '10:30', { estado: 'CANCELADO', cambioPaciente: 'CANCELO' }), turno('b', '11:00', '11:30')];
    expect(nuevosVisibles(turnos, true).map((t) => t.id)).toEqual(['a', 'c']);
    expect(nuevosVisibles(turnos, false).map((t) => t.id)).toEqual(['a']);
  });
});

describe('accionesDelTurno (spec detalle-turno)', () => {
  const ahora = new Date(en('12:10'));
  it('reservado futuro: confirmar, reprogramar, ajustar duración, editar nota y cancelar, sin marcar ausente', () => {
    expect(accionesDelTurno('RESERVADO', en('16:00'), ahora)).toEqual(['CONFIRMAR', 'REPROGRAMAR', 'AJUSTAR_DURACION', 'EDITAR_NOTA', 'CANCELAR']);
  });

  it('confirmado que ya empezó: suma marcar ausente y no tiene confirmar', () => {
    expect(accionesDelTurno('CONFIRMADO', en('12:00'), ahora)).toEqual(['REPROGRAMAR', 'AJUSTAR_DURACION', 'MARCAR_AUSENTE', 'EDITAR_NOTA', 'CANCELAR']);
  });

  it('cancelado o ausente: solo editar nota', () => {
    expect(accionesDelTurno('CANCELADO', en('16:00'), ahora)).toEqual(['EDITAR_NOTA']);
    expect(accionesDelTurno('AUSENTE', en('09:00'), ahora)).toEqual(['EDITAR_NOTA']);
  });
});

describe('vistaDeLaUrl (D6)', () => {
  const hoy = '2026-11-20';
  it('sin parámetros: día de hoy en mobile y semana actual (desde el lunes) en desktop', () => {
    expect(vistaDeLaUrl(new URLSearchParams(''), false, hoy)).toEqual({ vista: 'dia', fecha: '2026-11-20' });
    expect(vistaDeLaUrl(new URLSearchParams(''), true, hoy)).toEqual({ vista: 'semana', fecha: '2026-11-16' });
  });

  it('respeta la vista y la fecha pedidas; la semana se lleva a su lunes', () => {
    expect(vistaDeLaUrl(new URLSearchParams('vista=dia&fecha=2026-11-21'), true, hoy)).toEqual({ vista: 'dia', fecha: '2026-11-21' });
    expect(vistaDeLaUrl(new URLSearchParams('vista=semana&fecha=2026-11-19'), false, hoy)).toEqual({ vista: 'semana', fecha: '2026-11-16' });
  });

  it('una fecha o una vista inválidas caen en los valores por defecto', () => {
    expect(vistaDeLaUrl(new URLSearchParams('vista=mes&fecha=2026-13-45'), false, hoy)).toEqual({ vista: 'dia', fecha: '2026-11-20' });
    expect(vistaDeLaUrl(new URLSearchParams('fecha=ayer'), true, hoy)).toEqual({ vista: 'semana', fecha: '2026-11-16' });
  });
});
