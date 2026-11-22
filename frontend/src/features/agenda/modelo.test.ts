import { describe, expect, it } from 'vitest';
import { avisoDeNuevos, conteoPorDia, lineaDelDia, nombreCorto, rangoDeHoras, resumenDelDia, turnosDelDia, type BloqueoAgenda, type Fila, type TurnoAgenda } from './modelo';
import { instanteLocal } from './tiempo';

// Tareas 2.1 a 2.3 de C-13 (design D3 a D5; mockup `agenda.html`). Datos ficticios. Vitest corre con TZ=Europe/Madrid.
const BA = 'America/Argentina/Buenos_Aires';
const DIA = '2026-11-20'; // viernes
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

const HORARIO = [1, 2, 3, 4, 5].flatMap((diaSemana) => [
  { diaSemana, horaInicio: '09:00', horaFin: '13:00' },
  { diaSemana, horaInicio: '15:00', horaFin: '19:00' },
]);
/** Por defecto, "ahora" es otro día: así la marca de ahora no aparece salvo en los tests que la piden. */
const OTRO_DIA = new Date(en('12:00', '2026-11-10'));

const resumir = (filas: readonly Fila[]) =>
  filas.map((f) => {
    switch (f.tipo) {
      case 'turno':
        return `turno ${f.turno.id}${f.superponeCon === undefined ? '' : ` (con ${f.superponeCon.hora} · ${f.superponeCon.paciente})`}`;
      case 'bloqueo':
        return `bloqueo ${f.bloqueo.id}`;
      case 'libre':
        return `libre ${f.desde}-${f.hasta}`;
      case 'fuera':
        return `fuera ${f.desde}-${f.hasta}`;
      case 'ahora':
        return `ahora ${f.hora}`;
    }
  });

const dia = (turnos: TurnoAgenda[], extra: { bloqueos?: BloqueoAgenda[]; ahora?: Date; mostrarCancelados?: boolean; fecha?: string } = {}) =>
  resumir(
    lineaDelDia({
      dia: extra.fecha ?? DIA,
      zona: BA,
      turnos,
      bloqueos: extra.bloqueos ?? [],
      franjas: HORARIO,
      ahora: extra.ahora ?? OTRO_DIA,
      mostrarCancelados: extra.mostrarCancelados ?? true,
    }),
  );

describe('lineaDelDia', () => {
  it('ordena turnos y bloqueos con los libres y el tramo fuera de horario entre franjas', () => {
    const bloqueo: BloqueoAgenda = { id: 'b1', inicio: en('15:00'), fin: en('16:00'), motivo: 'Reunión con el laboratorio' };

    expect(dia([turno('t2', '09:30', '10:15'), turno('t1', '09:00', '09:30'), turno('t3', '10:30', '11:30')], { bloqueos: [bloqueo] })).toEqual([
      'turno t1',
      'turno t2',
      'libre 10:15-10:30',
      'turno t3',
      'libre 11:30-13:00',
      'fuera 13:00-15:00',
      'bloqueo b1',
      'libre 16:00-19:00',
    ]);
  });

  it('un día con horario y sin turnos muestra solo sus franjas libres', () => {
    expect(dia([])).toEqual(['libre 09:00-13:00', 'fuera 13:00-15:00', 'libre 15:00-19:00']);
  });

  it('un día sin horario de atención no tiene libres ni tramos fuera de horario', () => {
    expect(dia([], { fecha: '2026-11-22' })).toEqual([]);
  });

  it('un sobreturno va justo después del turno con el que se superpone y lo indica', () => {
    const base = turno('t1', '10:30', '11:30', { paciente: { nombre: 'Carla', apellido: 'Ficticia' } });
    const sobre = turno('s1', '10:45', '11:05', { esSobreturno: true, estado: 'RESERVADO' });

    expect(dia([sobre, base])).toContain('turno s1 (con 10:30 · Carla F.)');
    const filas = dia([sobre, base]);
    expect(filas.indexOf('turno s1 (con 10:30 · Carla F.)')).toBe(filas.indexOf('turno t1') + 1);
  });

  it('con los cancelados visibles ocupan su lugar; ocultos, desaparecen y el hueco queda libre', () => {
    const cancelado = turno('c1', '11:30', '12:00', { estado: 'CANCELADO', canceladoPor: 'PACIENTE' });

    expect(dia([cancelado])).toEqual(['libre 09:00-11:30', 'turno c1', 'libre 12:00-13:00', 'fuera 13:00-15:00', 'libre 15:00-19:00']);
    expect(dia([cancelado], { mostrarCancelados: false })).toEqual(['libre 09:00-13:00', 'fuera 13:00-15:00', 'libre 15:00-19:00']);
  });

  it('hoy muestra la marca de ahora entre el turno anterior y el siguiente', () => {
    const filas = dia([turno('t1', '11:30', '12:00'), turno('t2', '16:00', '16:30')], { ahora: new Date(en('12:10')) });

    expect(filas).toContain('ahora 12:10');
    expect(filas.indexOf('ahora 12:10')).toBe(filas.indexOf('turno t1') + 1);
  });

  it('otro día que no es hoy no tiene marca de ahora', () => {
    expect(dia([], { ahora: new Date(en('12:10', '2026-11-19')) }).some((f) => f.startsWith('ahora'))).toBe(false);
  });

  it('usa la zona del consultorio aunque el dispositivo esté en otra (13:00 UTC = 10:00 en Buenos Aires)', () => {
    const t = turno('t1', '10:00', '10:30');
    expect(t.inicio).toBe('2026-11-20T13:00:00.000Z');
    expect(dia([t])[1]).toBe('turno t1');
    expect(dia([t])[0]).toBe('libre 09:00-10:00');
  });
});

describe('turnosDelDia y resumen', () => {
  const turnos = [
    turno('t1', '09:00', '09:30'),
    turno('s1', '09:10', '09:20', { esSobreturno: true }),
    turno('c1', '11:30', '12:00', { estado: 'CANCELADO' }),
    turno('otro', '10:00', '10:30', { inicio: en('10:00', '2026-11-19'), fin: en('10:30', '2026-11-19') }),
  ];

  it('filtra por el día local', () => {
    expect(turnosDelDia(DIA, BA, turnos).map((t) => t.id)).toEqual(['t1', 's1', 'c1']);
  });

  it('cuenta turnos sin cancelados, sobreturnos y cancelados solo si se muestran', () => {
    const delDia = turnosDelDia(DIA, BA, turnos);
    expect(resumenDelDia(delDia, true)).toEqual({ turnos: 2, sobreturnos: 1, cancelados: 1 });
    expect(resumenDelDia(delDia, false)).toEqual({ turnos: 2, sobreturnos: 1, cancelados: 0 });
  });
});

describe('conteoPorDia', () => {
  it('cuenta los turnos de cada día de la semana para la tira de días', () => {
    const turnos = [turno('a', '09:00', '09:30'), turno('b', '10:00', '10:30', { estado: 'CANCELADO' }), turno('c', '09:00', '09:30', { inicio: en('09:00', '2026-11-16'), fin: en('09:30', '2026-11-16') })];

    expect(conteoPorDia('2026-11-16', BA, turnos, true).map((d) => [d.dia, d.cantidad])).toEqual([
      ['2026-11-16', 1],
      ['2026-11-17', 0],
      ['2026-11-18', 0],
      ['2026-11-19', 0],
      ['2026-11-20', 2],
      ['2026-11-21', 0],
      ['2026-11-22', 0],
    ]);
    expect(conteoPorDia('2026-11-16', BA, turnos, false)[4]?.cantidad).toBe(1);
  });
});

describe('avisoDeNuevos', () => {
  it('resume cuántos cambios hubo y quiénes, sin ver todavía (DD-30)', () => {
    const turnos = [
      turno('a', '16:00', '16:30', { cambioPaciente: 'RESERVO', paciente: { nombre: 'Federico', apellido: 'Ejemplo' } }),
      turno('b', '18:00', '18:30', { cambioPaciente: 'REPROGRAMO', paciente: { nombre: 'Hernán', apellido: 'Muestra' } }),
      turno('c', '11:30', '12:00', { cambioPaciente: 'CANCELO', estado: 'CANCELADO', paciente: { nombre: 'Elena', apellido: 'Demo' } }),
      turno('d', '09:00', '09:30'),
    ];

    expect(avisoDeNuevos(turnos)).toEqual({ titulo: '3 cambios de tus pacientes', detalle: 'Federico E. reservó, Hernán M. reprogramó y Elena D. canceló.' });
  });

  it('en singular con uno y nada sin cambios', () => {
    expect(avisoDeNuevos([turno('a', '16:00', '16:30', { cambioPaciente: 'RESERVO' })])?.titulo).toBe('1 cambio de tus pacientes');
    expect(avisoDeNuevos([turno('a', '16:00', '16:30')])).toBeNull();
  });
});

describe('nombreCorto', () => {
  it('nombre e inicial del apellido', () => {
    expect(nombreCorto({ nombre: 'Carla', apellido: 'Ficticia' })).toBe('Carla F.');
  });
});

describe('rangoDeHoras (D4)', () => {
  it('de 08:00 a 20:00 por defecto', () => {
    expect(rangoDeHoras(HORARIO, [], BA)).toEqual({ desde: 8 * 60, hasta: 20 * 60 });
  });

  it('se amplía a la disponibilidad y a los turnos de la semana, en horas enteras', () => {
    const temprano = turno('t', '07:15', '07:45');
    expect(rangoDeHoras([{ diaSemana: 6, horaInicio: '09:00', horaFin: '21:30' }], [temprano], BA)).toEqual({ desde: 7 * 60, hasta: 22 * 60 });
  });
});
