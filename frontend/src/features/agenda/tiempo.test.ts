import { describe, expect, it } from 'vitest';
import { diaDeSemana, diaLocal, horaLocal, instanteLocal, lunesDe, minutosLocales, sumarDias } from './tiempo';

// Base de la tarea 2.1 de C-13 (RN-TE-05): todo en la zona del consultorio. Vitest corre con TZ=Europe/Madrid.
const BA = 'America/Argentina/Buenos_Aires';

describe('instanteLocal', () => {
  it('convierte un día y una hora de Buenos Aires (UTC-3) a UTC', () => {
    expect(instanteLocal('2026-11-20', '10:00', BA).toISOString()).toBe('2026-11-20T13:00:00.000Z');
    expect(instanteLocal('2026-11-20', '00:00', BA).toISOString()).toBe('2026-11-20T03:00:00.000Z');
  });

  it('respeta una zona con horario de verano', () => {
    expect(instanteLocal('2026-07-01', '10:00', 'Europe/Madrid').toISOString()).toBe('2026-07-01T08:00:00.000Z');
    expect(instanteLocal('2026-01-15', '10:00', 'Europe/Madrid').toISOString()).toBe('2026-01-15T09:00:00.000Z');
  });
});

describe('día y hora locales de un instante', () => {
  it('usan la zona del consultorio y no la del dispositivo', () => {
    expect(horaLocal('2026-11-20T13:00:00Z', BA)).toBe('10:00');
    expect(diaLocal('2026-11-21T02:30:00Z', BA)).toBe('2026-11-20');
    expect(minutosLocales('2026-11-20T13:05:00Z', BA)).toBe(10 * 60 + 5);
  });
});

describe('calendario', () => {
  it('día de la semana de lunes (1) a domingo (7)', () => {
    expect(diaDeSemana('2026-11-16')).toBe(1);
    expect(diaDeSemana('2026-11-20')).toBe(5);
    expect(diaDeSemana('2026-11-22')).toBe(7);
  });

  it('suma días cruzando meses y años', () => {
    expect(sumarDias('2026-11-30', 1)).toBe('2026-12-01');
    expect(sumarDias('2026-01-01', -1)).toBe('2025-12-31');
  });

  it('el lunes de la semana de un día', () => {
    expect(lunesDe('2026-11-20')).toBe('2026-11-16');
    expect(lunesDe('2026-11-16')).toBe('2026-11-16');
    expect(lunesDe('2026-11-22')).toBe('2026-11-16');
  });
});
