import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { montarAplicacion, type Aplicacion } from './app';

const problema = (status: number, codigo: string) =>
  new Response(JSON.stringify({ status, codigo }), { status, headers: { 'Content-Type': 'application/problem+json' } });

describe('montarAplicacion', () => {
  let app: Aplicacion | undefined;

  beforeEach(() => {
    document.body.innerHTML = '<div id="app"></div>';
    history.replaceState(null, '', '/app/agenda');
  });
  afterEach(() => {
    app?.detener();
    app = undefined;
    vi.unstubAllGlobals();
  });

  it('monta el shell dentro de #app', async () => {
    app = montarAplicacion(document);
    await app.listo;
    expect(document.querySelector('#app [data-app-root]')).not.toBeNull();
    expect(document.querySelector('#app main#contenido')).not.toBeNull();
  });

  it('no duplica el shell si se monta dos veces', async () => {
    montarAplicacion(document).detener();
    app = montarAplicacion(document);
    await app.listo;
    expect(document.querySelectorAll('[data-app-root]')).toHaveLength(1);
  });

  it('falla con un mensaje claro si falta #app', () => {
    document.body.innerHTML = '';
    expect(() => montarAplicacion(document)).toThrow('No se encontró el elemento #app');
  });

  it('/app abre la agenda reemplazando la URL', async () => {
    history.replaceState(null, '', '/app');
    app = montarAplicacion(document);
    await app.listo;
    expect(location.pathname).toBe('/app/agenda');
    expect(document.querySelector('h1')?.textContent).toBe('Agenda');
    expect(document.querySelector('[data-nav-inferior] [aria-current="page"]')?.getAttribute('href')).toBe('/app/agenda');
  });

  it('navegar entre secciones cambia el título y la sección activa', async () => {
    app = montarAplicacion(document);
    await app.listo;
    await app.router.navegar('/app/bloqueos');
    expect(document.querySelector('h1')?.textContent).toBe('Bloqueos');
    expect(document.querySelector('[data-nav-lateral] [aria-current="page"]')?.getAttribute('href')).toBe('/app/bloqueos');
  });

  it('una URL desconocida muestra "No encontramos esta página" con "Ir a mi agenda"', async () => {
    history.replaceState(null, '', '/app/no-existe');
    app = montarAplicacion(document);
    await app.listo;
    const main = document.querySelector('main');
    expect(main?.querySelector('h2')?.textContent).toBe('No encontramos esta página');
    expect(main?.querySelector('a[href="/app/agenda"]')?.textContent).toBe('Ir a mi agenda');
    expect(document.querySelectorAll('[aria-current="page"]')).toHaveLength(0);
  });

  it('si el refresh falla, borra el token y lleva a /login conservando la ruta', async () => {
    const fetchFalso = vi.fn(async (entrada: RequestInfo | URL) =>
      String(entrada).endsWith('/auth/refresh') ? problema(401, 'REFRESH_INVALIDO') : problema(401, 'NO_AUTENTICADO'),
    );
    app = montarAplicacion(document, { fetch: fetchFalso });
    await app.listo;
    app.http.establecerToken('token-viejo');

    await expect(app.http.get('/api/v1/agenda')).rejects.toMatchObject({ codigo: 'SESION_VENCIDA' });

    await vi.waitFor(() => expect(`${location.pathname}${location.search}`).toBe('/login?volver=/app/agenda'));
    expect(app.http.tieneToken()).toBe(false);
  });

  it('al volver la conexión, la página vuelve a pedir sus datos', async () => {
    app = montarAplicacion(document);
    await app.listo;
    const recargar = vi.spyOn(app.router, 'recargar');

    window.dispatchEvent(new Event('offline'));
    expect(recargar).not.toHaveBeenCalled();
    window.dispatchEvent(new Event('online'));

    expect(recargar).toHaveBeenCalledTimes(1);
  });

  it('la hora de la última agenda sale del encabezado Date de la respuesta', async () => {
    const fetchFalso = vi.fn(async () => new Response('{}', { headers: { 'Content-Type': 'application/json', Date: 'Tue, 24 Nov 2026 13:32:00 GMT' } }));
    app = montarAplicacion(document, { fetch: fetchFalso });
    await app.listo;

    await app.http.get('/api/v1/agenda');
    window.dispatchEvent(new Event('offline'));

    expect(document.querySelector('[data-aviso-sin-conexion]')?.textContent).toContain('Mostrando tu agenda de las 10:32.');
    window.dispatchEvent(new Event('online'));
  });

  it('si un pedido falla por falta de red, muestra el aviso aunque navigator.onLine diga lo contrario', async () => {
    app = montarAplicacion(document, { fetch: vi.fn(() => Promise.reject(new TypeError('Failed to fetch'))) });
    await app.listo;

    await app.http.get('/api/v1/agenda').catch(() => undefined);

    expect(document.querySelector<HTMLElement>('[data-aviso-sin-conexion]')?.hidden).toBe(false);
    window.dispatchEvent(new Event('online'));
  });

  it('cerrar sesión revoca el refresh, borra token y caché de la agenda, y va a /login', async () => {
    const borrar = vi.fn<(nombre: string) => Promise<boolean>>().mockResolvedValue(true);
    vi.stubGlobal('caches', { delete: borrar });
    const fetchFalso = vi.fn(async () => new Response(null, { status: 204 }));
    app = montarAplicacion(document, { fetch: fetchFalso });
    await app.listo;
    app.http.establecerToken('token-1');

    await app.cerrarSesion();

    expect(fetchFalso).toHaveBeenCalledWith('/api/v1/auth/logout', expect.objectContaining({ method: 'POST', credentials: 'include' }));
    expect(borrar).toHaveBeenCalledWith('agenda-v1');
    expect(app.http.tieneToken()).toBe(false);
    expect(location.pathname).toBe('/login');
  });

  it('si la sesión vence, también borra la caché de la agenda', async () => {
    const borrar = vi.fn<(nombre: string) => Promise<boolean>>().mockResolvedValue(true);
    vi.stubGlobal('caches', { delete: borrar });
    const fetchFalso = vi.fn(async (entrada: RequestInfo | URL) =>
      String(entrada).endsWith('/auth/refresh') ? problema(401, 'REFRESH_INVALIDO') : problema(401, 'NO_AUTENTICADO'),
    );
    app = montarAplicacion(document, { fetch: fetchFalso });
    await app.listo;
    app.http.establecerToken('token-viejo');

    await app.http.get('/api/v1/agenda').catch(() => undefined);

    await vi.waitFor(() => expect(borrar).toHaveBeenCalledWith('agenda-v1'));
  });

  it('iniciar una sesión nueva borra la caché de la sesión anterior antes de guardar el token', async () => {
    const orden: string[] = [];
    const borrar = vi.fn(async (nombre: string) => {
      orden.push(`borrar:${nombre}`);
      return true;
    });
    vi.stubGlobal('caches', { delete: borrar });
    app = montarAplicacion(document);
    await app.listo;
    const http = app.http;
    const establecer = vi.spyOn(http, 'establecerToken').mockImplementation(() => {
      orden.push('token');
    });

    await app.iniciarSesion('token-nuevo');

    expect(orden).toEqual(['borrar:agenda-v1', 'token']);
    expect(establecer).toHaveBeenCalledWith('token-nuevo');
  });

  it('si no se puede borrar la caché, iniciar sesión igual guarda el token', async () => {
    vi.stubGlobal('caches', { delete: vi.fn().mockRejectedValue(new Error('SecurityError')) });
    app = montarAplicacion(document);
    await app.listo;

    await app.iniciarSesion('token-nuevo');

    expect(app.http.tieneToken()).toBe(true);
  });

  it('cerrar sesión sin conexión igual borra la caché local', async () => {
    const borrar = vi.fn<(nombre: string) => Promise<boolean>>().mockResolvedValue(true);
    vi.stubGlobal('caches', { delete: borrar });
    app = montarAplicacion(document, { fetch: vi.fn(() => Promise.reject(new TypeError('Failed to fetch'))) });
    await app.listo;

    await app.cerrarSesion();

    expect(borrar).toHaveBeenCalledWith('agenda-v1');
  });
});
