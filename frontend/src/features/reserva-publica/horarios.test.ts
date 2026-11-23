import { describe, expect, it } from 'vitest';
import { agruparHorarios, cuandoCorto, cuandoLargo } from './horarios';

// El entorno de tests corre con TZ=Europe/Madrid (vite.config.ts): si algo usara la zona del dispositivo, fallaría.
describe('horarios en la zona del consultorio (design D4)', () => {
  it('Córdoba: el hueco de las 13:30Z se muestra 10:30, en la mañana, con el instante intacto', () => {
    expect(agruparHorarios(['2026-11-24T13:30:00Z'], 'America/Argentina/Cordoba')).toEqual({
      manana: [{ instante: '2026-11-24T13:30:00Z', hora: '10:30' }],
      tarde: [],
    });
  });

  it('separa mañana y tarde a las 13 en hora del consultorio y respeta el orden de la API', () => {
    const grupos = agruparHorarios(
      ['2026-11-24T12:00:00Z', '2026-11-24T15:45:00Z', '2026-11-24T16:00:00Z', '2026-11-24T21:15:00Z'],
      'America/Argentina/Buenos_Aires',
    );
    expect(grupos.manana.map((h) => h.hora)).toEqual(['9:00', '12:45']);
    expect(grupos.tarde.map((h) => h.hora)).toEqual(['13:00', '18:15']);
  });

  it('arma los textos del botón y del resumen', () => {
    expect(cuandoCorto('2026-11-24T13:30:00Z', 'America/Argentina/Buenos_Aires')).toBe('martes 24, 10:30');
    expect(cuandoLargo('2026-11-24T13:30:00Z', 'America/Argentina/Buenos_Aires')).toBe('Martes 24 de noviembre, 10:30');
  });
});
