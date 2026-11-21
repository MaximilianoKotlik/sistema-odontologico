import { afterEach, describe, expect, it } from 'vitest';
import fuenteBrand from '../../../design-system/brand.js?raw';
import { leerNombreDeMarca } from '../pwa/opciones';
import { nombreDeMarca } from './marca';

/** El nombre tal como está escrito en brand.js: el test no repite el nombre, así un renombre no lo rompe. */
const nombreEnBrandJs = () => leerNombreDeMarca(fuenteBrand);

describe('nombreDeMarca', () => {
  const original: unknown = Reflect.get(window, 'BRAND_NAME');
  afterEach(() => {
    Reflect.set(window, 'BRAND_NAME', original);
  });

  it('lee la constante de design-system/brand.js', () => {
    expect(nombreDeMarca()).toBe(nombreEnBrandJs());
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
