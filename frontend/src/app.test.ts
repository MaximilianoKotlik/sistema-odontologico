import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { montarAplicacion, type Aplicacion, type DependenciasApp } from './app';
import type { CanalMinimo } from './features/auth/coordinacion';

const problema = (status: number, codigo: string) =>
  new Response(JSON.stringify({ status, codigo }), { status, headers: { 'Content-Type': 'application/problem+json' } });
const json = (cuerpo: unknown, extra: HeadersInit = {}) => new Response(JSON.stringify(cuerpo), { headers: { 'Content-Type': 'application/json', ...extra } });
const ruta = () => `${location.pathname}${location.search}`;

/** Backend falso: el refresh responde según `sesion`, el logout con 204 y lo demás con `{}`. */
function crearFetch(opciones: { sesion?: 'valida' | 'vencida' | 'sin-red'; resto?: (url: string) => Response | Promise<Response> } = {}) {
  return vi.fn(async (entrada: RequestInfo | URL) => {
    const url = String(entrada);
    if (url === '/api/v1/auth/refresh') {
      if (opciones.sesion === 'sin-red') throw new TypeError('Failed to fetch');
      return (opciones.sesion ?? 'valida') === 'valida' ? json({ accessToken: 'token-restaurado' }) : problema(401, 'SESION_EXPIRADA');
    }
    if (url === '/api/v1/auth/logout') return new Response(null, { status: 204 });
    return opciones.resto?.(url) ?? json({});
  });
}

/** BroadcastChannel falso compartido entre "pestañas" (instancias de la app en el mismo test). */
function crearHub() {
  const canales = new Set<CanalPrueba>();
  class CanalPrueba implements CanalMinimo {
    readonly oyentes = new Set<(e: MessageEvent) => void>();
    readonly enviados: unknown[] = [];
    constructor(readonly name: string) {
      canales.add(this);
    }
    postMessage(m: unknown) {
      this.enviados.push(m);
      for (const c of canales) if (c !== this && c.name === this.name) for (const o of c.oyentes) o(new MessageEvent('message', { data: m }));
    }
    addEventListener(_t: 'message', o: (e: MessageEvent) => void) {
      this.oyentes.add(o);
    }
    removeEventListener(_t: 'message', o: (e: MessageEvent) => void) {
      this.oyentes.delete(o);
    }
    close() {
      canales.delete(this);
    }
  }
  const creados: CanalPrueba[] = [];
  return {
    creados,
    fabrica: (nombre: string) => {
      const c = new CanalPrueba(nombre);
      creados.push(c);
      return c;
    },
    /** Lo que publicaría otra pestaña. */
    otraPestana: () => new CanalPrueba('muelas-auth'),
  };
}

describe('montarAplicacion', () => {
  let app: Aplicacion | undefined;

  const montar = async (dependencias: DependenciasApp = {}) => {
    app = montarAplicacion(document, { fetch: crearFetch(), canalSesion: null, locks: null, ...dependencias });
    await app.listo;
    return app;
  };

  beforeEach(() => {
    document.body.innerHTML = '<div id="app"></div>';
    history.replaceState(null, '', '/app/agenda');
  });
  afterEach(() => {
    app?.detener();
    app = undefined;
    vi.unstubAllGlobals();
  });

  describe('con la sesión restaurada', () => {
    it('monta el shell dentro de #app', async () => {
      await montar();
      expect(document.querySelector('#app [data-app-root]')).not.toBeNull();
      expect(document.querySelector('#app main#contenido')).not.toBeNull();
    });

    it('no duplica el shell si se monta dos veces', async () => {
      const primera = montarAplicacion(document, { fetch: crearFetch(), canalSesion: null, locks: null });
      await primera.listo;
      primera.detener();
      await montar();
      expect(document.querySelectorAll('[data-app-root]')).toHaveLength(1);
    });

    it('/app abre la agenda reemplazando la URL', async () => {
      history.replaceState(null, '', '/app');
      await montar();
      expect(location.pathname).toBe('/app/agenda');
      expect(document.querySelector('h1')?.textContent).toBe('Agenda');
      expect(document.querySelector('[data-nav-inferior] [aria-current="page"]')?.getAttribute('href')).toBe('/app/agenda');
    });

    it('navegar entre secciones cambia el título y la sección activa', async () => {
      const a = await montar();
      await a.router.navegar('/app/bloqueos');
      expect(document.querySelector('h1')?.textContent).toBe('Bloqueos');
      expect(document.querySelector('[data-nav-lateral] [aria-current="page"]')?.getAttribute('href')).toBe('/app/bloqueos');
    });

    it('una URL desconocida dentro de /app muestra "No encontramos esta página"', async () => {
      history.replaceState(null, '', '/app/no-existe');
      await montar();
      const main = document.querySelector('main');
      expect(main?.querySelector('h2')?.textContent).toBe('No encontramos esta página');
      expect(main?.querySelector('a[href="/app/agenda"]')?.textContent).toBe('Ir a mi agenda');
    });

    it('al volver la conexión, la página vuelve a pedir sus datos', async () => {
      const a = await montar();
      const recargar = vi.spyOn(a.router, 'recargar');
      window.dispatchEvent(new Event('offline'));
      expect(recargar).not.toHaveBeenCalled();
      window.dispatchEvent(new Event('online'));
      expect(recargar).toHaveBeenCalledTimes(1);
    });

    it('la hora de la última agenda sale del encabezado Date de la respuesta', async () => {
      const a = await montar({ fetch: crearFetch({ resto: () => json({}, { Date: 'Tue, 24 Nov 2026 13:32:00 GMT' }) }) });
      await a.http.get('/api/v1/agenda');
      window.dispatchEvent(new Event('offline'));
      expect(document.querySelector('[data-aviso-sin-conexion]')?.textContent).toContain('Mostrando tu agenda de las 10:32.');
      window.dispatchEvent(new Event('online'));
    });

    it('si un pedido falla por falta de red, muestra el aviso aunque navigator.onLine diga lo contrario', async () => {
      const a = await montar({ fetch: crearFetch({ resto: () => Promise.reject(new TypeError('Failed to fetch')) }) });
      await a.http.get('/api/v1/agenda').catch(() => undefined);
      expect(document.querySelector<HTMLElement>('[data-aviso-sin-conexion]')?.hidden).toBe(false);
      window.dispatchEvent(new Event('online'));
    });

    it('el menú de cuenta ofrece "Cambiar contraseña" arriba de "Cerrar sesión"', async () => {
      await montar();
      const cuenta = document.querySelector('[data-nav-lateral] [data-cuenta]');
      const textos = [...(cuenta?.querySelectorAll('a, button') ?? [])].map((e) => e.textContent);
      expect(textos).toEqual(['Cambiar contraseña', 'Cerrar sesión']);
      expect(cuenta?.querySelector('a')?.getAttribute('href')).toBe('/app/cuenta/contrasena');
    });
  });

  describe('arranque y guardas (D3)', () => {
    it('falla con un mensaje claro si falta #app', () => {
      document.body.innerHTML = '';
      expect(() => montarAplicacion(document)).toThrow('No se encontró el elemento #app');
    });

    it('mientras restaura la sesión muestra "Abriendo tu agenda…" con aria-busy y sin el shell', async () => {
      let responder: (r: Response) => void = () => undefined;
      const fetch = vi.fn((entrada: RequestInfo | URL) =>
        String(entrada) === '/api/v1/auth/refresh' ? new Promise<Response>((r) => (responder = r)) : Promise.resolve(json({})),
      );
      app = montarAplicacion(document, { fetch, canalSesion: null, locks: null });
      await new Promise((r) => setTimeout(r, 0));

      const splash = document.querySelector('[aria-busy="true"]');
      expect(splash?.textContent).toContain('Abriendo tu agenda…');
      expect(document.querySelector('[data-app-root]')).toBeNull();

      responder(json({ accessToken: 'token-restaurado' }));
      await app.listo;
      expect(document.querySelector('[data-app-root]')).not.toBeNull();
      expect(app.http.tieneToken()).toBe(true);
    });

    it('sin sesión, una ruta privada va al login con volver y sin aviso de error', async () => {
      history.replaceState(null, '', '/app/configuracion');
      await montar({ fetch: crearFetch({ sesion: 'vencida' }) });
      expect(ruta()).toBe('/login?volver=/app/configuracion');
      expect(document.querySelector('[data-app-root]')).toBeNull();
      expect(document.querySelector('h1')?.textContent).toBe('Ingresá a tu agenda');
      expect(document.querySelector('[role="status"], [role="alert"]')).toBeNull();
    });

    it('abrir /login desde cero no pide refresh y muestra el formulario', async () => {
      history.replaceState(null, '', '/login');
      const fetch = crearFetch();
      await montar({ fetch });
      expect(fetch.mock.calls.map(([url]) => String(url))).not.toContain('/api/v1/auth/refresh');
      expect(document.querySelector('form')).not.toBeNull();
    });

    it('con token en memoria, navegar a /login o /registro lleva a /app', async () => {
      const a = await montar();
      await a.router.navegar('/login');
      expect(location.pathname).toBe('/app/agenda');
      await a.router.navegar('/registro');
      expect(location.pathname).toBe('/app/agenda');
    });

    it('una ruta desconocida fuera de /app va al login sin sesión', async () => {
      history.replaceState(null, '', '/cualquiera');
      await montar();
      expect(location.pathname).toBe('/login');
    });

    it('si el refresh falla por falta de red, monta el shell sin conexión para leer la agenda guardada', async () => {
      await montar({ fetch: crearFetch({ sesion: 'sin-red' }) });
      expect(document.querySelector('[data-app-root]')).not.toBeNull();
      expect(document.querySelector<HTMLElement>('[data-aviso-sin-conexion]')?.hidden).toBe(false);
      window.dispatchEvent(new Event('online'));
    });
  });

  describe('vencimiento, cierre e inicio de sesión', () => {
    it('si la sesión vence en uso, borra token y caché y va al login con "Tu sesión venció" conservando la ruta', async () => {
      const borrar = vi.fn<(nombre: string) => Promise<boolean>>().mockResolvedValue(true);
      vi.stubGlobal('caches', { delete: borrar });
      let refrescos = 0;
      const fetch = vi.fn(async (entrada: RequestInfo | URL) => {
        const url = String(entrada);
        if (url === '/api/v1/auth/refresh') return (refrescos += 1) === 1 ? json({ accessToken: 't' }) : problema(401, 'SESION_EXPIRADA');
        // El arranque consulta las prestaciones para decidir si abre el asistente (C-09, D3).
        if (url === '/api/v1/prestaciones' && refrescos === 1) return json([{ id: 'p-1', nombre: 'Consulta', duracionMin: 30, activa: true }]);
        return problema(401, 'NO_AUTENTICADO');
      });
      const a = await montar({ fetch });

      await expect(a.http.get('/api/v1/agenda')).rejects.toMatchObject({ codigo: 'SESION_VENCIDA' });

      await vi.waitFor(() => expect(document.querySelector('[data-auth-root]')).not.toBeNull());
      expect(ruta()).toBe('/login?volver=/app/agenda');
      expect(a.http.tieneToken()).toBe(false);
      expect(borrar).toHaveBeenCalledWith('agenda-v1');
      expect(document.querySelector('[data-app-root]')).toBeNull();
      expect(document.querySelector('[role="status"]')?.textContent).toContain('Tu sesión venció.');
    });

    it('cerrar sesión revoca el refresh, borra token y caché, desmonta el shell y avisa en el login', async () => {
      const borrar = vi.fn<(nombre: string) => Promise<boolean>>().mockResolvedValue(true);
      vi.stubGlobal('caches', { delete: borrar });
      const fetch = crearFetch();
      const a = await montar({ fetch });

      await a.cerrarSesion();

      expect(fetch).toHaveBeenCalledWith('/api/v1/auth/logout', expect.objectContaining({ method: 'POST', credentials: 'include' }));
      expect(borrar).toHaveBeenCalledWith('agenda-v1');
      expect(a.http.tieneToken()).toBe(false);
      expect(location.pathname).toBe('/login');
      expect(document.querySelector('[data-app-root]')).toBeNull();
      expect(document.querySelector('[role="status"]')?.textContent).toContain('Cerraste sesión.');
    });

    it('cerrar sesión sin conexión igual borra lo local y va al login', async () => {
      const borrar = vi.fn<(nombre: string) => Promise<boolean>>().mockResolvedValue(true);
      vi.stubGlobal('caches', { delete: borrar });
      const fetch = vi.fn(async (entrada: RequestInfo | URL) => {
        if (String(entrada) === '/api/v1/auth/refresh') return json({ accessToken: 't' });
        throw new TypeError('Failed to fetch');
      });
      const a = await montar({ fetch });

      await a.cerrarSesion();

      expect(borrar).toHaveBeenCalledWith('agenda-v1');
      expect(location.pathname).toBe('/login');
    });

    it('iniciar una sesión nueva borra la caché de la sesión anterior antes de guardar el token', async () => {
      const orden: string[] = [];
      vi.stubGlobal('caches', {
        delete: vi.fn(async (nombre: string) => {
          orden.push(`borrar:${nombre}`);
          return true;
        }),
      });
      const a = await montar();
      vi.spyOn(a.http, 'establecerToken').mockImplementation(() => {
        orden.push('token');
      });

      await a.iniciarSesion('token-nuevo');

      expect(orden).toEqual(['borrar:agenda-v1', 'token']);
    });

    it('si no se puede borrar la caché, iniciar sesión igual guarda el token', async () => {
      vi.stubGlobal('caches', { delete: vi.fn().mockRejectedValue(new Error('SecurityError')) });
      history.replaceState(null, '', '/login');
      const a = await montar();

      await a.iniciarSesion('token-nuevo');

      expect(a.http.tieneToken()).toBe(true);
    });
  });

  describe('asistente de primer ingreso (C-09, D3)', () => {
    const consulta = { id: 'p-1', nombre: 'Consulta', duracionMin: 30, activa: true, visiblePublica: true, orden: 1 };
    const conPrestaciones = (lista: unknown) =>
      crearFetch({
        resto: (url) => {
          if (url === '/api/v1/prestaciones') return json(lista);
          if (url === '/api/v1/auth/login') return json({ accessToken: 'token-login' });
          if (url === '/api/v1/consultorio') return json({ nombre: 'Consultorio Demo Sur', slug: 'consultorio-demo-sur' });
          return json({});
        },
      });

    it('al restaurar la sesión sin prestaciones activas abre el asistente, sin la navegación del shell', async () => {
      await montar({ fetch: conPrestaciones([]) });
      await vi.waitFor(() => expect(location.pathname).toBe('/app/bienvenida'));
      await vi.waitFor(() => expect(document.querySelector('a[href="/app/agenda"]')?.textContent).toBe('Terminar después'));
      expect(document.querySelector('[data-app-root]')).toBeNull();
      expect(document.querySelector('[data-nav-inferior]')).toBeNull();
    });

    it('con prestaciones activas abre la ruta pedida como siempre', async () => {
      await montar({ fetch: conPrestaciones([consulta]) });
      expect(location.pathname).toBe('/app/agenda');
      expect(document.querySelector('[data-app-root]')).not.toBeNull();
    });

    it('si no se puede saber si hay prestaciones, no se interpone', async () => {
      await montar({ fetch: conPrestaciones({ error: 'algo' }) });
      expect(location.pathname).toBe('/app/agenda');
    });

    it('después del login sin prestaciones activas va al asistente', async () => {
      history.replaceState(null, '', '/login?volver=/app/configuracion');
      await montar({ fetch: conPrestaciones([]) });
      const formulario = document.querySelector('form');
      const email = document.querySelector<HTMLInputElement>('input[name="email"]');
      const password = document.querySelector<HTMLInputElement>('input[name="password"]');
      if (email === null || password === null || formulario === null) throw new Error('falta el formulario');
      email.value = 'odontologa@example.com';
      password.value = 'ficticia-segura-01';
      formulario.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));

      await vi.waitFor(() => expect(location.pathname).toBe('/app/bienvenida'));
    });

    it('"Terminar después" lleva a la agenda y no vuelve a abrir el asistente en esa sesión', async () => {
      const a = await montar({ fetch: conPrestaciones([]) });
      await vi.waitFor(() => expect(location.pathname).toBe('/app/bienvenida'));

      await a.router.navegar('/app/agenda');

      expect(location.pathname).toBe('/app/agenda');
      expect(document.querySelector('[data-app-root]')).not.toBeNull();
    });
  });

  describe('cierre de sesión entre pestañas (D11)', () => {
    it('al cerrar sesión publica solo el tipo y el motivo', async () => {
      const hub = crearHub();
      const a = await montar({ canalSesion: hub.fabrica });

      await a.cerrarSesion();

      expect(hub.creados[0]?.name).toBe('muelas-auth');
      expect(hub.creados[0]?.enviados).toEqual([{ tipo: 'sesion-cerrada', motivo: 'logout' }]);
    });

    it('otra pestaña que cerró sesión: borra token, shell y caché, y lleva al login con "Cerraste sesión"', async () => {
      const borrar = vi.fn<(nombre: string) => Promise<boolean>>().mockResolvedValue(true);
      vi.stubGlobal('caches', { delete: borrar });
      const hub = crearHub();
      const a = await montar({ canalSesion: hub.fabrica });

      hub.otraPestana().postMessage({ tipo: 'sesion-cerrada', motivo: 'logout' });

      await vi.waitFor(() => expect(document.querySelector('[data-auth-root]')).not.toBeNull());
      expect(location.pathname).toBe('/login');
      expect(a.http.tieneToken()).toBe(false);
      expect(borrar).toHaveBeenCalledWith('agenda-v1');
      expect(document.querySelector('[data-app-root]')).toBeNull();
      expect(document.querySelector('[role="status"]')?.textContent).toContain('Cerraste sesión.');
    });

    it('otra pestaña que cambió la contraseña: lleva al login con "Cambiaste tu contraseña"', async () => {
      const hub = crearHub();
      await montar({ canalSesion: hub.fabrica });

      hub.otraPestana().postMessage({ tipo: 'sesion-cerrada', motivo: 'contrasena-cambiada' });

      await vi.waitFor(() => expect(document.querySelector('[role="status"]')?.textContent).toContain('Cambiaste tu contraseña.'));
    });

    it('en una ruta pública, el aviso no la cambia', async () => {
      const hub = crearHub();
      history.replaceState(null, '', '/recuperar');
      await montar({ canalSesion: hub.fabrica });

      hub.otraPestana().postMessage({ tipo: 'sesion-cerrada', motivo: 'logout' });
      await new Promise((r) => setTimeout(r, 0));

      expect(location.pathname).toBe('/recuperar');
    });

    it('un mensaje con otro formato se ignora', async () => {
      const hub = crearHub();
      const a = await montar({ canalSesion: hub.fabrica });

      hub.otraPestana().postMessage({ tipo: 'sesion-cerrada', motivo: 'otro' });
      await new Promise((r) => setTimeout(r, 0));

      expect(a.http.tieneToken()).toBe(true);
      expect(location.pathname).toBe('/app/agenda');
    });
  });
});
