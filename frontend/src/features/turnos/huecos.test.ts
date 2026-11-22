import { describe, expect, it } from 'vitest';
import type { BloqueoAgenda, FranjaDisponible, TurnoAgenda } from '../agenda/modelo';
import { iniciosLibres } from './huecos';

// Tarea 2.4 de C-14 (design D6, revisión de Maxi en #31): huecos para reprogramar, con la misma regla que
// `CalculadoraDeHuecos` de C-12 para el odontólogo. Viernes 20 de noviembre, en Buenos Aires (UTC-3). Datos ficticios.
const BA = 'America/Argentina/Buenos_Aires';
const FRANJAS: FranjaDisponible[] = [
  { diaSemana: 5, horaInicio: '09:00', horaFin: '13:00' },
  { diaSemana: 5, horaInicio: '15:00', horaFin: '19:00' },
];

const turno = (id: string, desde: string, hasta: string, extra: Partial<TurnoAgenda> = {}): TurnoAgenda => ({
  id,
  inicio: `2026-11-20T${desde}:00-03:00`,
  fin: `2026-11-20T${hasta}:00-03:00`,
  duracionMin: 30,
  estado: 'RESERVADO',
  esSobreturno: false,
  origen: 'MANUAL',
  prestacion: 'Consulta',
  paciente: { nombre: 'Carla', apellido: 'Ficticia' },
  tieneNota: false,
  canceladoPor: null,
  cambioPaciente: null,
  novedadPacienteAt: null,
  ...extra,
});

const base = {
  dia: '2026-11-20',
  zona: BA,
  franjas: FRANJAS,
  duracionMin: 30,
  paso: 15,
  ahora: new Date('2026-11-19T12:00:00Z'),
};

describe('iniciosLibres', () => {
  it('avanza de a paso desde el inicio de cada franja mientras entra el turno completo', () => {
    const inicios = iniciosLibres({ ...base, turnos: [], bloqueos: [] });
    expect(inicios[0]).toBe('09:00');
    expect(inicios).toContain('12:30');
    expect(inicios).not.toContain('12:45');
    expect(inicios.at(-1)).toBe('18:30');
    expect(inicios).toHaveLength(15 * 2);
  });

  it('descarta los que pisan un turno activo o un bloqueo', () => {
    const bloqueo: BloqueoAgenda = { id: 'b-1', inicio: '2026-11-20T15:00:00-03:00', fin: '2026-11-20T16:00:00-03:00', motivo: null };
    const inicios = iniciosLibres({ ...base, turnos: [turno('t-1', '10:00', '11:00')], bloqueos: [bloqueo] });
    expect(inicios).toContain('09:30');
    expect(inicios).not.toContain('09:45');
    expect(inicios).not.toContain('10:30');
    expect(inicios).toContain('11:00');
    expect(inicios).not.toContain('15:30');
    expect(inicios).toContain('16:00');
  });

  it('no cuentan los cancelados, los ausentes ni los sobreturnos, ni el turno que se mueve', () => {
    const inicios = iniciosLibres({
      ...base,
      excluir: 't-mueve',
      turnos: [turno('t-c', '09:00', '10:00', { estado: 'CANCELADO' }), turno('t-s', '10:00', '11:00', { esSobreturno: true }), turno('t-mueve', '11:00', '11:30')],
      bloqueos: [],
    });
    expect(inicios).toEqual(expect.arrayContaining(['09:00', '10:00', '11:00']));
  });

  it('no ofrece el pasado', () => {
    const inicios = iniciosLibres({ ...base, ahora: new Date('2026-11-20T12:10:00-03:00'), turnos: [], bloqueos: [] });
    expect(inicios[0]).toBe('12:15');
  });

  it('sin horario ese día o sin horario cargado, no hay huecos', () => {
    expect(iniciosLibres({ ...base, dia: '2026-11-22', turnos: [], bloqueos: [] })).toEqual([]);
    expect(iniciosLibres({ ...base, franjas: null, turnos: [], bloqueos: [] })).toEqual([]);
  });
});
