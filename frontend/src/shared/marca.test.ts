import { afterEach, describe, expect, it } from 'vitest';
import { nombreDeMarca } from './marca';

describe('nombreDeMarca', () => {
  const original: unknown = Reflect.get(window, 'BRAND_NAME');
  afterEach(() => {
    Reflect.set(window, 'BRAND_NAME', original);
  });

  it('lee la constante de design-system/brand.js', () => {
    expect(nombreDeMarca()).toBe('Turnos Odonto (TBD)');
  });

  it('refleja un cambio de la constante', () => {
    Reflect.set(window, 'BRAND_NAME', 'Nombre Nuevo');
    expect(nombreDeMarca()).toBe('Nombre Nuevo');
  });

  it('falla con un mensaje claro si brand.js no definió el nombre', () => {
    Reflect.set(window, 'BRAND_NAME', undefined);
    expect(() => nombreDeMarca()).toThrow('BRAND_NAME');
  });
});
