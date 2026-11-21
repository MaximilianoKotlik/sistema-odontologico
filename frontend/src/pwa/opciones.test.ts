import { describe, expect, it } from 'vitest';
import { CACHE_AGENDA, crearOpcionesPwa, leerNombreDeMarca } from './opciones';

const opciones = crearOpcionesPwa('Turnos Odonto (TBD)');
const workbox = opciones.workbox ?? {};
const reglas = workbox.runtimeCaching ?? [];

function coincide(patron: unknown, ruta: string, metodo = 'GET'): boolean {
  if (!(patron instanceof Function)) throw new Error('Se esperaba un urlPattern función');
  const url = new URL(ruta, 'https://app.example.com');
  const request = new Request(url, { method: metodo });
  return Boolean(patron({ url, request, sameOrigin: true, event: new Event('fetch') }));
}

describe('leerNombreDeMarca', () => {
  it('extrae BRAND_NAME de design-system/brand.js', () => {
    expect(leerNombreDeMarca('var BRAND_NAME = "Turnos Odonto (TBD)";')).toBe('Turnos Odonto (TBD)');
  });

  it('falla si brand.js no define el nombre', () => {
    expect(() => leerNombreDeMarca('var OTRA = 1;')).toThrow('BRAND_NAME');
  });
});

describe('manifiesto (pwa-offline: App instalable)', () => {
  it('usa el nombre de la marca, standalone y arranca en /app/agenda', () => {
    expect(opciones.manifest).toMatchObject({
      name: 'Turnos Odonto (TBD)',
      lang: 'es-AR',
      display: 'standalone',
      start_url: '/app/agenda',
    });
  });

  it('declara íconos de 192 y 512 px', () => {
    const manifiesto = opciones.manifest;
    const tamanos = manifiesto === false || manifiesto === undefined ? [] : (manifiesto.icons ?? []).map((i) => i.sizes);
    expect(tamanos).toEqual(expect.arrayContaining(['192x192', '512x512']));
  });
});

describe('Workbox (pwa-offline: política de caché)', () => {
  it('usa generateSW y precachea el shell', () => {
    expect(opciones.strategies ?? 'generateSW').toBe('generateSW');
    expect(workbox.globPatterns).toEqual(expect.arrayContaining([expect.stringContaining('js')]));
  });

  it('tiene una sola regla de runtime: NetworkFirst para la agenda', () => {
    expect(reglas).toHaveLength(1);
    expect(reglas[0]).toMatchObject({
      handler: 'NetworkFirst',
      method: 'GET',
      options: {
        cacheName: CACHE_AGENDA,
        networkTimeoutSeconds: 4,
        expiration: { maxEntries: 30, maxAgeSeconds: 7 * 24 * 60 * 60 },
        cacheableResponse: { statuses: [200] },
      },
    });
  });

  it('la regla cachea GET /api/v1/agenda con sus parámetros', () => {
    expect(coincide(reglas[0]?.urlPattern, '/api/v1/agenda?desde=2026-11-24')).toBe(true);
  });

  it('nunca cachea auth, la API pública ni la gestión por token', () => {
    const patron = reglas[0]?.urlPattern;
    for (const ruta of ['/api/v1/auth/refresh', '/api/v1/public/turnos/abc', '/api/v1/public/c/demo', '/t/abc', '/api/v1/agendas-falsas']) {
      expect(coincide(patron, ruta), ruta).toBe(false);
    }
  });

  it('no guarda las respuestas con datos personales de la agenda (C-12, revisión de Maxi en #39)', () => {
    // La búsqueda de pacientes y el detalle del turno traen DNI y teléfono: solo GET /api/v1/agenda va a la caché.
    const patron = reglas[0]?.urlPattern;
    for (const ruta of [
      '/api/v1/pacientes?q=90000001',
      '/api/v1/turnos/0192f3a4-0000-7000-8000-000000000001',
      '/api/v1/turnos/fuera-de-disponibilidad',
      '/api/v1/bloqueos?desde=2026-11-23T03:00:00Z',
    ]) {
      expect(coincide(patron, ruta), ruta).toBe(false);
    }
  });

  it('no aplica la estrategia a métodos que no sean GET', () => {
    expect(reglas.every((r) => (r.method ?? 'GET') === 'GET')).toBe(true);
  });

  it('el fallback de navegación excluye la API y las páginas públicas', () => {
    expect(workbox.navigateFallback).toBe('/index.html');
    const denegadas = workbox.navigateFallbackDenylist ?? [];
    for (const ruta of ['/api/v1/agenda', '/c/consultorio-demo', '/t/token-123']) {
      expect(denegadas.some((re) => re.test(ruta)), ruta).toBe(true);
    }
    expect(denegadas.some((re) => re.test('/app/agenda'))).toBe(false);
  });
});
