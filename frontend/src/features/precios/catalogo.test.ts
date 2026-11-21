import { describe, expect, it } from 'vitest';
import { esCatalogoPublico, formatearActualizacion, formatearImporteArs } from './catalogo';
import { catalogoDeEjemplo } from './catalogo-de-ejemplo';

const sinEspaciosRaros = (texto: string) => texto.replace(/\s/g, ' ');

describe('formatearImporteArs', () => {
  it('formatea un importe entero en pesos con formato es-AR, sin decimales', () => {
    expect(sinEspaciosRaros(formatearImporteArs('14900.00'))).toBe('$ 14.900');
  });

  it('conserva los centavos exactos cuando los hay, sin pasar por float', () => {
    expect(sinEspaciosRaros(formatearImporteArs('1234567.05'))).toBe('$ 1.234.567,05');
    expect(sinEspaciosRaros(formatearImporteArs('9007199254740993.99'))).toBe('$ 9.007.199.254.740.993,99');
  });

  it('rechaza un importe que no es texto decimal con dos decimales', () => {
    for (const malo of ['PENDIENTE', '14900', '14900.5', '-1.00', '14.900,00', '']) {
      expect(() => formatearImporteArs(malo), malo).toThrow(RangeError);
    }
  });
});

describe('formatearActualizacion', () => {
  it('muestra el instante UTC como fecha larga en Buenos Aires', () => {
    expect(formatearActualizacion('2026-11-20T03:00:00Z')).toBe('20 de noviembre de 2026');
  });

  it('usa la zona de Buenos Aires y no la UTC', () => {
    expect(formatearActualizacion('2026-11-20T02:59:00Z')).toBe('19 de noviembre de 2026');
  });
});

describe('esCatalogoPublico', () => {
  it('acepta un catálogo con la forma del contrato', () => {
    expect(esCatalogoPublico(catalogoDeEjemplo)).toBe(true);
  });

  it('rechaza respuestas que no cumplen el contrato', () => {
    const plan = catalogoDeEjemplo.planes[0];
    const invalidos: unknown[] = [
      null,
      'texto',
      {},
      { ...catalogoDeEjemplo, planes: [] },
      { ...catalogoDeEjemplo, planes: [{ ...plan, importeARS: 14900 }] },
      { ...catalogoDeEjemplo, planes: [{ ...plan, importeARS: 'PENDIENTE' }] },
      { ...catalogoDeEjemplo, planes: [{ ...plan, moneda: 'USD' }] },
      { ...catalogoDeEjemplo, planes: [{ ...plan, impuestosIncluidos: false }] },
      { ...catalogoDeEjemplo, planes: [{ ...plan, incluye: [1] }] },
      { ...catalogoDeEjemplo, prueba: { dias: '30', requiereMedioDePago: false } },
      { ...catalogoDeEjemplo, prueba: { dias: 30, requiereMedioDePago: true } },
      { ...catalogoDeEjemplo, actualizadoEn: 'ayer' },
    ];
    for (const valor of invalidos) {
      expect(esCatalogoPublico(valor), JSON.stringify(valor)).toBe(false);
    }
  });

  it('acepta un plan sin descripción', () => {
    const plan = { ...catalogoDeEjemplo.planes[0] };
    delete (plan as { descripcion?: string }).descripcion;
    expect(esCatalogoPublico({ ...catalogoDeEjemplo, planes: [plan] })).toBe(true);
  });
});
