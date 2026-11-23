import { describe, expect, it } from 'vitest';
import { hrefTelefono, telefonoLegible } from './telefono';

describe('teléfono del consultorio', () => {
  it('muestra un celular de CABA en E.164 como se escribe acá', () => {
    expect(telefonoLegible('+5491100000001')).toBe('11 0000-0001');
    expect(telefonoLegible('+541100000001')).toBe('11 0000-0001');
  });

  it('otro número queda como vino, sin inventar dónde corta la característica', () => {
    expect(telefonoLegible('+5493514000042')).toBe('+5493514000042');
  });

  it('el link tel: lleva solo dígitos y el +', () => {
    expect(hrefTelefono('+54 9 11 0000-0001')).toBe('tel:+5491100000001');
  });
});
