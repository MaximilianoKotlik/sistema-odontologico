import { describe, expect, it } from 'vitest';
import { sanearVolver } from './volver';

// Tarea 1.2 de C-07 (design D5): validar después de normalizar.
const ORIGEN = 'https://app.muelas.test';
const BARRA_INVERTIDA = '\\';

describe('sanearVolver', () => {
  it('acepta /app y las rutas debajo de /app/', () => {
    expect(sanearVolver('/app', ORIGEN)).toBe('/app');
    expect(sanearVolver('/app/configuracion', ORIGEN)).toBe('/app/configuracion');
    expect(sanearVolver('/app/configuracion/horarios', ORIGEN)).toBe('/app/configuracion/horarios');
  });

  it('conserva la query y el fragmento de una ruta interna', () => {
    expect(sanearVolver('/app/agenda?fecha=2026-11-23#turno-1', ORIGEN)).toBe('/app/agenda?fecha=2026-11-23#turno-1');
  });

  it('devuelve la ruta normalizada, nunca el texto original', () => {
    expect(sanearVolver('/app/x/../agenda', ORIGEN)).toBe('/app/agenda');
  });

  it('descarta el vacío y la ausencia de valor', () => {
    expect(sanearVolver('', ORIGEN)).toBe('/app');
    expect(sanearVolver(null, ORIGEN)).toBe('/app');
  });

  it('descarta otros dominios y protocolos', () => {
    for (const malo of [
      'https://otro.example.com',
      'https://otro.example.com/app',
      '//otro.example.com/app',
      `/${BARRA_INVERTIDA}otro.example.com/app`,
      `${BARRA_INVERTIDA}${BARRA_INVERTIDA}otro.example.com/app`,
      'javascript:alert(1)',
      'JavaScript:alert(1)',
      'data:text/html,hola',
    ]) {
      expect(sanearVolver(malo, ORIGEN), malo).toBe('/app');
    }
  });

  it('descarta las rutas públicas y las que solo se parecen a /app', () => {
    for (const malo of ['/', '/login', '/registro', '/recuperar', '/terminos', '/application', '/app-falsa', 'app/agenda']) {
      expect(sanearVolver(malo, ORIGEN), malo).toBe('/app');
    }
  });

  it('descarta las rutas que salen de /app al normalizarse', () => {
    for (const malo of [
      '/app/../login',
      '/app/%2e%2e/login',
      '/app/%2E%2E/login',
      '/app/.%2e/login',
      `/app${BARRA_INVERTIDA}..${BARRA_INVERTIDA}login`,
    ]) {
      expect(sanearVolver(malo, ORIGEN), malo).toBe('/app');
    }
  });

  it('descarta una URL absoluta aunque sea del mismo origen: volver es siempre una ruta', () => {
    expect(sanearVolver(`${ORIGEN}/login`, ORIGEN)).toBe('/app');
    expect(sanearVolver(`${ORIGEN}/app/agenda`, ORIGEN)).toBe('/app');
  });
});
