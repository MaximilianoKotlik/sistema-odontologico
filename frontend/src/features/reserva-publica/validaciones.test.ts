import { describe, expect, it } from 'vitest';
import { normalizarDni, validarDatos, type DatosDelFormulario } from './validaciones';

const validos: DatosDelFormulario = {
  nombre: 'Ana',
  apellido: 'Ejemplo',
  dni: '30.000.001',
  telefono: '11 0000-0042',
  email: 'ana@example.com',
  acepta: true,
};

const campos = (datos: Partial<DatosDelFormulario>) => {
  const resultado = validarDatos({ ...validos, ...datos });
  return resultado.ok ? [] : resultado.errores.map((e) => e.campo);
};

describe('normalizarDni (RN-RP-03, design D5)', () => {
  it.each([
    ['30.000.001', '30000001'],
    ['30000001', '30000001'],
    [' 3 000 001 ', '3000001'],
  ])('%s → %s', (texto, dni) => {
    expect(normalizarDni(texto)).toBe(dni);
  });

  it.each(['12.34', '123456', '123456789', '30-000-001', 'abc', ''])('rechaza %s', (texto) => {
    expect(normalizarDni(texto)).toBeNull();
  });
});

describe('validarDatos', () => {
  it('con datos válidos arma el pedido con el DNI solo con dígitos y el email recortado', () => {
    const resultado = validarDatos({ ...validos, nombre: '  Ana ', email: ' ana@example.com ' });
    expect(resultado).toEqual({
      ok: true,
      datos: { nombre: 'Ana', apellido: 'Ejemplo', dni: '30000001', telefono: '11 0000-0042', aceptaPrivacidad: true, email: 'ana@example.com' },
    });
  });

  it('el email es opcional: vacío no se envía', () => {
    const resultado = validarDatos({ ...validos, email: '   ' });
    expect(resultado.ok && !('email' in resultado.datos)).toBe(true);
  });

  it('el escenario del spec: DNI 12.34, celular de 3 dígitos y casilla sin marcar dan 3 errores en orden', () => {
    const resultado = validarDatos({ ...validos, dni: '12.34', telefono: '123', acepta: false });
    expect(resultado.ok).toBe(false);
    if (resultado.ok) return;
    expect(resultado.errores).toEqual([
      { campo: 'dni', resumen: 'El DNI tiene que tener 7 u 8 números.', mensaje: 'El DNI tiene que tener 7 u 8 números, por ejemplo 30.000.001.' },
      { campo: 'telefono', resumen: 'Falta la característica en el celular.', mensaje: 'Escribilo con característica, por ejemplo 11 0000-0042.' },
      { campo: 'acepta', resumen: 'Tenés que aceptar el uso de tus datos.', mensaje: 'Para reservar tenés que aceptarlo.' },
    ]);
  });

  it('nombre y apellido son obligatorios y de hasta 80 caracteres', () => {
    expect(campos({ nombre: '  ' })).toEqual(['nombre']);
    expect(campos({ apellido: '' })).toEqual(['apellido']);
    expect(campos({ nombre: 'a'.repeat(80) })).toEqual([]);
    expect(campos({ nombre: 'a'.repeat(81) })).toEqual(['nombre']);
  });

  it.each(['11 0000-0042', '+54 9 11 0000-0042', '011 0000-0042', '(0351) 400-0042', '+5491100000042', '+34 600 000 042'])(
    'acepta el celular %s',
    (telefono) => {
      expect(campos({ telefono })).toEqual([]);
    },
  );

  it.each(['123', '0000-0042', '11 0000', 'once cero cero', '+54 11', ''])('rechaza el celular %s', (telefono) => {
    expect(campos({ telefono })).toEqual(['telefono']);
  });

  it('un email con formato inválido es un error; uno válido no', () => {
    expect(campos({ email: 'ana@' })).toEqual(['email']);
    expect(campos({ email: 'ana@example' })).toEqual(['email']);
    expect(campos({ email: 'ana@example.com' })).toEqual([]);
  });
});
