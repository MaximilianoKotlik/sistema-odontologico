import { afterEach, describe, expect, it, vi } from 'vitest';
import { borrarCacheAgenda, crearConectividad, registrarSincronizacion } from './conectividad';

describe('conectividad', () => {
  let detener: (() => void) | undefined;
  afterEach(() => {
    detener?.();
    vi.restoreAllMocks();
  });

  it('arranca con el estado del navegador', () => {
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false);
    const c = crearConectividad(window);
    detener = c.detener;
    expect(c.store.get()).toEqual({ online: false, ultimaSincronizacion: null });
  });

  it('sigue los eventos offline y online de la ventana', () => {
    const c = crearConectividad(window);
    detener = c.detener;

    window.dispatchEvent(new Event('offline'));
    expect(c.store.get().online).toBe(false);

    window.dispatchEvent(new Event('online'));
    expect(c.store.get().online).toBe(true);
  });

  it('detener deja de escuchar los eventos', () => {
    const c = crearConectividad(window);
    c.detener();
    window.dispatchEvent(new Event('offline'));
    expect(c.store.get().online).toBe(true);
  });
});

describe('registrarSincronizacion', () => {
  const respuesta = (fecha: string | null) => new Response('{}', { headers: fecha === null ? {} : { Date: fecha } });

  it('toma la hora del encabezado Date de la respuesta de la agenda (también si vino de la caché)', () => {
    const { store, detener } = crearConectividad(window);
    registrarSincronizacion(store, '/api/v1/agenda?dia=2026-11-24', respuesta('Tue, 24 Nov 2026 13:32:00 GMT'));
    expect(store.get().ultimaSincronizacion).toBe('2026-11-24T13:32:00.000Z');
    detener();
  });

  it('ignora respuestas de otras rutas o sin fecha válida', () => {
    const { store, detener } = crearConectividad(window);
    registrarSincronizacion(store, '/api/v1/bloqueos', respuesta('Tue, 24 Nov 2026 13:32:00 GMT'));
    registrarSincronizacion(store, '/api/v1/agenda', respuesta(null));
    registrarSincronizacion(store, '/api/v1/agenda', respuesta('no es fecha'));
    expect(store.get().ultimaSincronizacion).toBeNull();
    detener();
  });
});

describe('borrarCacheAgenda', () => {
  it('borra la caché agenda-v1 del Service Worker', async () => {
    const borrar = vi.fn<(nombre: string) => Promise<boolean>>().mockResolvedValue(true);
    await borrarCacheAgenda({ delete: borrar });
    expect(borrar).toHaveBeenCalledWith('agenda-v1');
  });

  it('no falla si el navegador no tiene Cache Storage', async () => {
    await expect(borrarCacheAgenda(undefined)).resolves.toBeUndefined();
  });
});
