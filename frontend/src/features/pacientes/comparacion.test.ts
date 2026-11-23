import { describe, expect, it } from 'vitest';
import { comparar, mismoNombre } from './comparacion';

// C-17, tarea 4.3 (design D7): la misma regla que `Paciente.datosDifierenDe` del backend, con sus mismos casos.
const ANA = { nombre: 'Ana', apellido: 'Ejemplo', email: 'ana@example.com' };

describe('mismoNombre', () => {
  it('no distingue mayúsculas ni espacios en los bordes', () => {
    expect(mismoNombre('Ana', ' ana ')).toBe(true);
    expect(mismoNombre('Prueba', 'PRUEBA')).toBe(true);
  });

  it('no distingue espacios repetidos', () => {
    expect(mismoNombre('José  María', 'jose maria')).toBe(true);
    expect(mismoNombre('Pérez   Gómez', 'perez gomez')).toBe(true);
  });

  it('no distingue tildes ni la virgulilla de la ñ', () => {
    expect(mismoNombre('José', ' JOSÉ ')).toBe(true);
    expect(mismoNombre('Jose', 'José')).toBe(true);
    expect(mismoNombre('Iñaki', 'Inaki')).toBe(true);
  });

  it('distingue otro nombre', () => {
    expect(mismoNombre('Ana', 'Ana María')).toBe(false);
    expect(mismoNombre('José', 'Josefa')).toBe(false);
    expect(mismoNombre('Ana', 'Anna')).toBe(false);
  });
});

describe('comparar', () => {
  it('con un solo dato distinto', () => {
    expect(comparar(ANA, { ...ANA, nombre: 'Anna' })).toEqual({ difieren: ['nombre'], coinciden: ['apellido', 'email'] });
  });

  it('con varios datos distintos, en el orden de la ficha', () => {
    expect(comparar(ANA, { nombre: 'Anna', apellido: 'Ejemplos', email: 'anna@example.com' })).toEqual({
      difieren: ['nombre', 'apellido', 'email'],
      coinciden: [],
    });
  });

  it('las mayúsculas, las tildes y los espacios no hacen que difieran', () => {
    expect(comparar(ANA, { nombre: ' ANÁ ', apellido: 'ejemplo', email: 'Ana@Example.com' })).toEqual({
      difieren: [],
      coinciden: ['nombre', 'apellido', 'email'],
    });
  });

  it('el email se compara sin mayúsculas pero con sus tildes', () => {
    expect(comparar(ANA, { ...ANA, email: 'ANA@EXAMPLE.COM' }).difieren).toEqual([]);
    expect(comparar(ANA, { ...ANA, email: 'aná@example.com' }).difieren).toEqual(['email']);
  });

  it('un email que falta de un lado difiere; sin email en los dos lados coincide', () => {
    expect(comparar(ANA, { ...ANA, email: null }).difieren).toEqual(['email']);
    expect(comparar({ ...ANA, email: null }, ANA).difieren).toEqual(['email']);
    expect(comparar({ ...ANA, email: null }, { nombre: 'Ana', apellido: 'Ejemplo' }).coinciden).toContain('email');
    expect(comparar({ ...ANA, email: '' }, { ...ANA, email: null }).coinciden).toContain('email');
  });
});
