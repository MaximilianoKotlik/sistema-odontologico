import { describe, expect, it } from 'vitest';
import { dniLegible, telefonoLegible } from './formato';

// Formatos de lectura de DNI y celular (C-14, mockup nuevo-turno). Datos ficticios.

describe('dniLegible', () => {
  it('agrupa de a miles', () => expect(dniLegible('30000010')).toBe('30.000.010'));
  it('deja tal cual lo que no son solo dígitos', () => expect(dniLegible('X123')).toBe('X123'));
});

describe('telefonoLegible', () => {
  it('un celular argentino en E.164 se lee como "+54 9 11 0000-0010"', () => expect(telefonoLegible('+5491100000010')).toBe('+54 9 11 0000-0010'));
  it('otro formato queda tal cual', () => expect(telefonoLegible('+12025550123')).toBe('+12025550123'));
});
