import { describe, expect, it } from 'vitest';
import { nombreDeZona } from './zona';

describe('nombreDeZona (design D4)', () => {
  it.each([
    ['America/Argentina/Buenos_Aires', 'Buenos Aires'],
    ['America/Argentina/Cordoba', 'Córdoba'],
    ['America/Argentina/Mendoza', 'Mendoza'],
    ['America/Argentina/San_Luis', 'San Luis'],
    ['America/Argentina/Salta', 'Salta'],
    ['America/Argentina/Ushuaia', 'Ushuaia'],
    ['America/Argentina/Tucuman', 'Tucumán'],
    ['America/Argentina/Rio_Gallegos', 'Río Gallegos'],
  ])('%s se lee como "%s"', (zona, nombre) => {
    expect(nombreDeZona(zona)).toBe(nombre);
  });

  it('una zona desconocida usa el identificador IANA como respaldo', () => {
    expect(nombreDeZona('Europe/Madrid')).toBe('Europe/Madrid');
  });
});
