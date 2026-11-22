import { describe, expect, it } from 'vitest';
import { diaElegible, etiquetaDia, proximoDiaConHorarios, quedanDias, sumarDias, ventanaDesde } from './dias';

const dia = (fecha: string, atiende: boolean, huecos: string[] = []) => ({ fecha, atiende, huecos });

describe('ventanas de 14 días hasta el horizonte (design D3)', () => {
  it('suma días calendario sin depender de la zona del dispositivo', () => {
    expect(sumarDias('2026-11-20', 13)).toBe('2026-12-03');
    expect(sumarDias('2026-12-31', 1)).toBe('2027-01-01');
  });

  it('la primera ventana va de hoy a hoy + 13, cortada en el horizonte', () => {
    expect(ventanaDesde('2026-11-20', '2027-01-19')).toEqual({ desde: '2026-11-20', hasta: '2026-12-03' });
    expect(ventanaDesde('2026-11-20', '2026-11-25')).toEqual({ desde: '2026-11-20', hasta: '2026-11-25' });
    expect(ventanaDesde('2026-11-26', '2026-11-25')).toBeNull();
  });

  it('"Ver más días" sigue mientras no se llegó al horizonte, aunque no haya huecos', () => {
    expect(quedanDias('2026-12-03', '2027-01-19')).toBe(true);
    expect(quedanDias('2026-12-19', '2026-12-19')).toBe(false);
    expect(quedanDias('2026-12-20', '2026-12-19')).toBe(false);
  });
});

describe('estado de cada día', () => {
  it('un sábado con huecos se puede elegir; un domingo sin atención o un día completo no', () => {
    expect(diaElegible(dia('2026-11-21', true, ['2026-11-21T12:00:00Z']))).toBe(true);
    expect(diaElegible(dia('2026-11-22', false))).toBe(false);
    expect(diaElegible(dia('2026-11-23', true, []))).toBe(false);
  });

  it('ofrece el próximo día con horarios después del elegido', () => {
    const dias = [dia('2026-11-23', true), dia('2026-11-24', false), dia('2026-11-25', true, ['2026-11-25T12:00:00Z'])];
    expect(proximoDiaConHorarios(dias, '2026-11-23')?.fecha).toBe('2026-11-25');
    expect(proximoDiaConHorarios(dias, '2026-11-25')).toBeNull();
  });

  it('arma las etiquetas del día en es-AR', () => {
    expect(etiquetaDia('2026-11-21')).toEqual({ semana: 'sáb', numero: '21', mes: 'noviembre 2026', largo: 'sábado 21' });
    expect(etiquetaDia('2026-11-22').largo).toBe('domingo 22');
  });
});
