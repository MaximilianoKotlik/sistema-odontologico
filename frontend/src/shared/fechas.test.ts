import { describe, expect, it } from 'vitest';
import { formatearDia, formatearHora, hoyEn } from './fechas';

const BUENOS_AIRES = 'America/Argentina/Buenos_Aires';

describe('fechas: presentación en la zona del consultorio', () => {
  it('los tests corren con el dispositivo en Europe/Madrid para detectar fugas de zona', () => {
    // 13:30 UTC son las 14:30 en Madrid (invierno): si esto falla, el entorno no está fijando TZ.
    expect(new Date('2026-11-24T13:30:00Z').getHours()).toBe(14);
  });

  it('muestra la hora en la zona del consultorio aunque el dispositivo esté en otra', () => {
    expect(formatearHora('2026-11-24T13:30:00Z', BUENOS_AIRES)).toBe('10:30');
  });

  it('formatea las horas en 24 h sin cero inicial ni "a. m."', () => {
    expect(formatearHora('2026-11-24T12:00:00Z', BUENOS_AIRES)).toBe('9:00');
    expect(formatearHora('2026-11-24T21:05:00Z', BUENOS_AIRES)).toBe('18:05');
  });

  it('acepta Date además de cadenas ISO', () => {
    expect(formatearHora(new Date('2026-11-24T03:00:00Z'), BUENOS_AIRES)).toBe('0:00');
  });

  it('respeta la zona que recibe (triangulación con otra zona)', () => {
    expect(formatearHora('2026-11-24T13:30:00Z', 'America/Mexico_City')).toBe('7:30');
  });
});

describe('fechas: formato del día en es-AR', () => {
  it('formatea un día calendario para el encabezado de la agenda', () => {
    expect(formatearDia('2026-11-24', BUENOS_AIRES)).toBe('martes 24 de noviembre');
  });

  it('formatea el día de un instante en la zona del consultorio', () => {
    // 01:00 UTC del 25 todavía es el martes 24 en Buenos Aires.
    expect(formatearDia('2026-11-25T01:00:00Z', BUENOS_AIRES)).toBe('martes 24 de noviembre');
  });

  it('un día calendario no se corre por la zona del dispositivo ni del consultorio', () => {
    expect(formatearDia('2026-12-01', 'Pacific/Kiritimati')).toBe('martes 1 de diciembre');
  });
});

describe('fechas: "hoy" según el consultorio', () => {
  it('cerca de la medianoche UTC, hoy sigue siendo el día del consultorio', () => {
    expect(hoyEn(BUENOS_AIRES, new Date('2026-11-25T01:00:00Z'))).toBe('2026-11-24');
  });

  it('después de la medianoche local cambia de día', () => {
    expect(hoyEn(BUENOS_AIRES, new Date('2026-11-25T03:00:00Z'))).toBe('2026-11-25');
  });

  it('rechaza una zona horaria inválida con un error claro', () => {
    expect(() => hoyEn('Marte/Olympus', new Date())).toThrow('Zona horaria inválida: Marte/Olympus');
  });
});
