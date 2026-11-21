import type { components } from '../../shared/api-types';

/** Respuesta de `GET /api/v1/public/planes` (C-21, design D3), tipada desde el contrato OpenAPI. */
export type CatalogoPublico = components['schemas']['PlanesPublicosResponse'];
export type PlanPublico = components['schemas']['PlanPublicoResponse'];

export const RUTA_PLANES_PUBLICOS = '/api/v1/public/planes';

/** Los precios se muestran siempre en la zona de Argentina, sea cual sea la del dispositivo (regla dura 14). */
const ZONA_ARGENTINA = 'America/Argentina/Buenos_Aires';
const IMPORTE = /^(\d+)\.(\d{2})$/;
const INSTANTE_UTC = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2}(\.\d+)?)?Z$/;

const pesos = new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS', minimumFractionDigits: 0, maximumFractionDigits: 0 });
const SEPARADOR_DECIMAL = new Intl.NumberFormat('es-AR').formatToParts(1.5).find((p) => p.type === 'decimal')?.value ?? ',';
const fechaLarga = new Intl.DateTimeFormat('es-AR', { timeZone: ZONA_ARGENTINA, day: 'numeric', month: 'long', year: 'numeric' });

/**
 * "14900.00" → "$ 14.900"; "1234567.05" → "$ 1.234.567,05".
 * El importe llega como texto exacto y nunca pasa por un número de punto flotante (regla dura 15):
 * la parte entera se formatea como BigInt y los centavos se agregan tal cual.
 */
export function formatearImporteArs(importe: string): string {
  const partes = IMPORTE.exec(importe);
  const entero = partes?.[1];
  const centavos = partes?.[2];
  if (entero === undefined || centavos === undefined) throw new RangeError(`Importe inválido: ${importe}`);

  const formateado = pesos.formatToParts(BigInt(entero));
  if (centavos === '00') return formateado.map((p) => p.value).join('');
  const ultimoEntero = formateado.map((p) => p.type).lastIndexOf('integer');
  return formateado.map((p, i) => (i === ultimoEntero ? `${p.value}${SEPARADOR_DECIMAL}${centavos}` : p.value)).join('');
}

/** Instante UTC → "20 de noviembre de 2026", en la zona de Buenos Aires. */
export function formatearActualizacion(instante: string): string {
  return fechaLarga.format(new Date(instante));
}

function esObjeto(valor: unknown): valor is Record<string, unknown> {
  return typeof valor === 'object' && valor !== null && !Array.isArray(valor);
}

const esTexto = (valor: unknown): valor is string => typeof valor === 'string' && valor.trim() !== '';

function esPlanPublico(valor: unknown): valor is PlanPublico {
  return (
    esObjeto(valor) &&
    esTexto(valor['id']) &&
    esTexto(valor['nombre']) &&
    (valor['descripcion'] === undefined || typeof valor['descripcion'] === 'string') &&
    Array.isArray(valor['incluye']) &&
    valor['incluye'].length > 0 &&
    valor['incluye'].every(esTexto) &&
    typeof valor['importeARS'] === 'string' &&
    IMPORTE.test(valor['importeARS']) &&
    valor['moneda'] === 'ARS' &&
    valor['impuestosIncluidos'] === true
  );
}

/**
 * La respuesta llega como JSON sin validar: solo se muestra si cumple el contrato. Una prueba que pidiera un
 * medio de pago o un importe que no es exacto se tratan como error (spec public-plans: no inventar importes).
 */
export function esCatalogoPublico(valor: unknown): valor is CatalogoPublico {
  if (!esObjeto(valor) || !esObjeto(valor['prueba'])) return false;
  const { planes, prueba, actualizadoEn } = { planes: valor['planes'], prueba: valor['prueba'], actualizadoEn: valor['actualizadoEn'] };
  return (
    Array.isArray(planes) &&
    planes.length > 0 &&
    planes.every(esPlanPublico) &&
    typeof prueba['dias'] === 'number' &&
    Number.isInteger(prueba['dias']) &&
    prueba['dias'] > 0 &&
    prueba['requiereMedioDePago'] === false &&
    typeof actualizadoEn === 'string' &&
    INSTANTE_UTC.test(actualizadoEn) &&
    !Number.isNaN(Date.parse(actualizadoEn))
  );
}
