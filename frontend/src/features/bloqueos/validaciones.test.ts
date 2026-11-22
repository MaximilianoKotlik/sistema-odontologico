import { describe, expect, it } from 'vitest';
import type { BloqueoAgenda } from '../agenda/modelo';
import { LARGO_MOTIVO_BLOQUEO, agruparBloqueos, describirBloqueo, rangoDelBloqueo, validarMotivoBloqueo } from './validaciones';

// Tarea 2.1 de C-14 (design D8; spec bloqueos; RN-AG-05). Zona del consultorio: Buenos Aires (UTC-3). Datos ficticios.
const BA = 'America/Argentina/Buenos_Aires';

describe('rangoDelBloqueo', () => {
  it('por horas: un día de desde a hasta, en instantes UTC', () => {
    expect(rangoDelBloqueo({ todoElDia: false, dia: '2026-11-20', desde: '15:00', hasta: '19:00', diaDesde: '', diaHasta: '' }, BA)).toEqual({
      ok: true,
      inicio: '2026-11-20T18:00:00.000Z',
      fin: '2026-11-20T22:00:00.000Z',
    });
  });

  it('días completos: del inicio del primer día al inicio del día siguiente al último (D8)', () => {
    expect(rangoDelBloqueo({ todoElDia: true, dia: '', desde: '', hasta: '', diaDesde: '2027-01-04', diaHasta: '2027-01-15' }, BA)).toEqual({
      ok: true,
      inicio: '2027-01-04T03:00:00.000Z',
      fin: '2027-01-16T03:00:00.000Z',
    });
  });

  it('un solo día completo', () => {
    const rango = rangoDelBloqueo({ todoElDia: true, dia: '', desde: '', hasta: '', diaDesde: '2026-11-20', diaHasta: '2026-11-20' }, BA);
    expect(rango).toMatchObject({ inicio: '2026-11-20T03:00:00.000Z', fin: '2026-11-21T03:00:00.000Z' });
  });

  it('marca el fin si termina antes de empezar o a la misma hora', () => {
    expect(rangoDelBloqueo({ todoElDia: false, dia: '2026-11-20', desde: '15:00', hasta: '14:00', diaDesde: '', diaHasta: '' }, BA)).toEqual({
      ok: false,
      errores: [{ campo: 'hasta', mensaje: 'Tiene que terminar después de las 15:00.' }],
    });
    expect(rangoDelBloqueo({ todoElDia: false, dia: '2026-11-20', desde: '15:00', hasta: '15:00', diaDesde: '', diaHasta: '' }, BA)).toMatchObject({ ok: false });
  });

  it('marca el último día si es anterior al primero', () => {
    expect(rangoDelBloqueo({ todoElDia: true, dia: '', desde: '', hasta: '', diaDesde: '2027-01-15', diaHasta: '2027-01-04' }, BA)).toEqual({
      ok: false,
      errores: [{ campo: 'diaHasta', mensaje: 'Tiene que ser el mismo día o uno posterior al primero.' }],
    });
  });

  it('pide los campos vacíos y horas en pasos de 5 minutos', () => {
    const resultado = rangoDelBloqueo({ todoElDia: false, dia: '', desde: '15:02', hasta: '', diaDesde: '', diaHasta: '' }, BA);
    expect(resultado.ok ? [] : resultado.errores.map((e) => e.campo)).toEqual(['dia', 'desde', 'hasta']);
  });
});

describe('validarMotivoBloqueo', () => {
  it('es opcional y admite hasta 120 caracteres (04)', () => {
    expect(LARGO_MOTIVO_BLOQUEO).toBe(120);
    expect(validarMotivoBloqueo('')).toBeNull();
    expect(validarMotivoBloqueo('a'.repeat(120))).toBeNull();
    expect(validarMotivoBloqueo('a'.repeat(121))).toBe('El motivo puede tener hasta 120 caracteres.');
  });
});

const bloqueo = (inicio: string, fin: string, motivo: string | null = 'Vacaciones'): BloqueoAgenda => ({ id: inicio, inicio, fin, motivo });

describe('describirBloqueo', () => {
  it('un rango de horas en un día', () => {
    expect(describirBloqueo(bloqueo('2026-11-20T18:00:00Z', '2026-11-20T19:00:00Z'), BA)).toBe('Viernes 20 de noviembre · 15:00 a 16:00');
  });

  it('días completos', () => {
    expect(describirBloqueo(bloqueo('2027-01-04T03:00:00Z', '2027-01-16T03:00:00Z'), BA)).toBe('Lunes 4 de enero a viernes 15 de enero · todo el día');
    expect(describirBloqueo(bloqueo('2026-11-20T03:00:00Z', '2026-11-21T03:00:00Z'), BA)).toBe('Viernes 20 de noviembre · todo el día');
  });

  it('varios días con horas', () => {
    expect(describirBloqueo(bloqueo('2026-11-20T18:00:00Z', '2026-11-21T13:00:00Z'), BA)).toBe('Viernes 20 de noviembre 15:00 a sábado 21 de noviembre 10:00');
  });
});

describe('agruparBloqueos', () => {
  it('separa los de esta semana de los que vienen más adelante, en orden de inicio', () => {
    const ahora = new Date('2026-11-20T15:10:00Z');
    const grupos = agruparBloqueos(
      [bloqueo('2027-01-04T03:00:00Z', '2027-01-16T03:00:00Z'), bloqueo('2026-11-26T12:00:00Z', '2026-11-26T13:30:00Z'), bloqueo('2026-11-20T18:00:00Z', '2026-11-20T19:00:00Z')],
      ahora,
      BA,
    );
    expect(grupos.map((g) => [g.titulo, g.bloqueos.map((b) => b.inicio)])).toEqual([
      ['Esta semana', ['2026-11-20T18:00:00Z']],
      ['Más adelante', ['2026-11-26T12:00:00Z', '2027-01-04T03:00:00Z']],
    ]);
  });

  it('omite los grupos vacíos', () => {
    expect(agruparBloqueos([bloqueo('2027-01-04T03:00:00Z', '2027-01-16T03:00:00Z')], new Date('2026-11-20T15:10:00Z'), BA).map((g) => g.titulo)).toEqual(['Más adelante']);
  });
});
