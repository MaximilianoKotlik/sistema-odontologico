import { describe, expect, it } from 'vitest';
import fuenteReal from '../../contenido/seguridad.json';
import { afirmaciones, esFuenteSeguridad, frasesProhibidas, sinRespaldo, textoPlano, type FuenteSeguridad } from './fuente';

const copia = (): FuenteSeguridad => JSON.parse(JSON.stringify(fuenteReal)) as FuenteSeguridad;

describe('2.1 la fuente versionada (design D1)', () => {
  it('la fuente real cumple el type guard', () => {
    expect(esFuenteSeguridad(fuenteReal)).toBe(true);
  });

  it('rechaza una afirmación sin texto', () => {
    const fuente = copia() as unknown as { temas: { bloques: { afirmacion?: unknown }[] }[] };
    const parrafo = fuente.temas.flatMap((t) => t.bloques).find((b) => b.afirmacion !== undefined);
    if (parrafo === undefined) throw new Error('la fuente no tiene párrafos');
    parrafo.afirmacion = { respaldo: ['RN-PL-06'], requiere: [] };
    expect(esFuenteSeguridad(fuente)).toBe(false);
  });

  it('rechaza una fecha de actualización que no es un día calendario', () => {
    expect(esFuenteSeguridad({ ...copia(), actualizadoEn: '20/11/2026' })).toBe(false);
  });

  it('tiene los ocho temas del mockup aprobado, en orden', () => {
    expect(copia().temas.map((t) => t.id)).toEqual(['roles', 'datos', 'aislamiento', 'auditoria', 'exportacion', 'acceso', 'hosting', 'falta']);
  });
});

describe('2.2 respaldo y frases prohibidas (design D4)', () => {
  it('cada afirmación de la fuente real declara su respaldo', () => {
    expect(sinRespaldo(copia())).toEqual([]);
    expect(afirmaciones(copia()).length).toBeGreaterThan(20);
  });

  it('una afirmación sin respaldo agregada a propósito se detecta', () => {
    const fuente = copia();
    const tema = fuente.temas[0];
    if (tema === undefined) throw new Error('sin temas');
    const conNueva: FuenteSeguridad = {
      ...fuente,
      temas: [{ ...tema, bloques: [...tema.bloques, { tipo: 'parrafo', afirmacion: { texto: ['Frase nueva sin respaldo.'], respaldo: [], requiere: [] } }] }, ...fuente.temas.slice(1)],
    };
    expect(sinRespaldo(conNueva)).toEqual(['Frase nueva sin respaldo.']);
  });

  it('la fuente real no tiene frases prohibidas', () => {
    expect(frasesProhibidas(copia())).toEqual([]);
  });

  it.each(['Estamos certificados en seguridad.', 'Cumplimos la ley de datos personales.', 'El sistema es 100 % seguro.', 'Tu contraseña cifrada está a salvo.', 'No guardamos datos de salud.'])(
    'detecta "%s"',
    (frase) => {
      const fuente = copia();
      const tema = fuente.temas[0];
      if (tema === undefined) throw new Error('sin temas');
      const conProhibida: FuenteSeguridad = {
        ...fuente,
        temas: [{ ...tema, bloques: [...tema.bloques, { tipo: 'parrafo', afirmacion: { texto: [frase], respaldo: ['prueba'], requiere: [] } }] }, ...fuente.temas.slice(1)],
      };
      expect(frasesProhibidas(conProhibida)).toEqual([frase]);
    },
  );

  it('decir que NO hay certificaciones ISO está permitido', () => {
    const texto = afirmaciones(copia()).map((a) => textoPlano(a.texto)).find((t) => t.includes('ISO 27001'));
    expect(texto).toContain('No tenemos certificaciones de seguridad');
  });
});
