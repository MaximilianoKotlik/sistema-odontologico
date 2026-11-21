import { describe, expect, it, vi } from 'vitest';
import { crearClienteHttp } from '../../shared/http';
import { crearCanalSesion, crearCoordinadorRefresh, esAvisoSesionCerrada, NOMBRE_CANAL, type CanalMinimo } from './coordinacion';

// Tarea 2.2 de C-07: refresh serializado entre pestañas (D6) y cierre de sesión entre pestañas (D11).

/** Web Locks falso: una cola FIFO por nombre, como `navigator.locks` con modo exclusivo. */
function crearLocksFalsos() {
  const colas = new Map<string, Promise<unknown>>();
  const nombres: string[] = [];
  return {
    nombres,
    request<T>(nombre: string, tarea: () => Promise<T>): Promise<T> {
      nombres.push(nombre);
      const anterior = colas.get(nombre) ?? Promise.resolve();
      const actual = anterior.then(tarea, tarea);
      colas.set(nombre, actual.catch(() => undefined));
      return actual;
    },
  };
}

/**
 * Backend falso con una cookie de refresh compartida por el "navegador": cada refresh válido la rota; presentar
 * una ya rotada revoca la familia (D4 de C-04) y responde 401.
 */
function crearBackendConCookie() {
  let cookieVigente = 1;
  let cookieDelNavegador = 1;
  let revocada = false;
  let emitidos = 0;
  const fetch = vi.fn(async (entrada: RequestInfo | URL) => {
    const url = String(entrada);
    if (url !== '/api/v1/auth/refresh') throw new Error(`Ruta inesperada ${url}`);
    const presentada = cookieDelNavegador;
    await new Promise((resolver) => setTimeout(resolver, 5));
    if (revocada || presentada !== cookieVigente) {
      revocada = true;
      return new Response(JSON.stringify({ codigo: 'SESION_EXPIRADA' }), { status: 401, headers: { 'Content-Type': 'application/problem+json' } });
    }
    cookieVigente += 1;
    cookieDelNavegador = cookieVigente;
    emitidos += 1;
    return new Response(JSON.stringify({ accessToken: `token-${emitidos}` }), { status: 200, headers: { 'Content-Type': 'application/json' } });
  });
  return { fetch, revocada: () => revocada };
}

describe('crearCoordinadorRefresh', () => {
  it('dos pestañas que renuevan a la vez lo hacen una después de la otra y ninguna pierde la sesión', async () => {
    const backend = crearBackendConCookie();
    const locks = crearLocksFalsos();
    const pestanaA = crearClienteHttp({ fetch: backend.fetch, coordinarRefresh: crearCoordinadorRefresh(locks) });
    const pestanaB = crearClienteHttp({ fetch: backend.fetch, coordinarRefresh: crearCoordinadorRefresh(locks) });

    const resultados = await Promise.all([pestanaA.renovarSesion(), pestanaB.renovarSesion()]);

    expect(resultados).toEqual([true, true]);
    expect(backend.revocada()).toBe(false);
    expect(locks.nombres).toEqual(['muelas-auth-refresh', 'muelas-auth-refresh']);
  });

  it('sin coordinación, las mismas dos pestañas revocan la sesión (el riesgo que resuelve D6)', async () => {
    const backend = crearBackendConCookie();
    const pestanaA = crearClienteHttp({ fetch: backend.fetch });
    const pestanaB = crearClienteHttp({ fetch: backend.fetch });

    await Promise.all([pestanaA.renovarSesion({ avisarSiVence: false }), pestanaB.renovarSesion({ avisarSiVence: false })]);

    expect(backend.revocada()).toBe(true);
  });

  it('sin Web Locks corre la tarea directamente', async () => {
    const coordinar = crearCoordinadorRefresh(null);

    await expect(coordinar(async () => 'hecho')).resolves.toBe('hecho');
  });
});

/** BroadcastChannel falso: los canales del mismo nombre se reparten los mensajes, salvo al emisor. */
function crearHubDeCanales() {
  const canales = new Set<CanalFalso>();
  class CanalFalso implements CanalMinimo {
    private readonly oyentes = new Set<(evento: MessageEvent) => void>();
    readonly enviados: unknown[] = [];
    constructor(readonly name: string) {
      canales.add(this);
    }
    postMessage(mensaje: unknown): void {
      this.enviados.push(mensaje);
      for (const otro of canales) {
        if (otro !== this && otro.name === this.name) otro.recibir(mensaje);
      }
    }
    recibir(mensaje: unknown): void {
      for (const oyente of this.oyentes) oyente(new MessageEvent('message', { data: mensaje }));
    }
    addEventListener(_tipo: 'message', oyente: (evento: MessageEvent) => void): void {
      this.oyentes.add(oyente);
    }
    removeEventListener(_tipo: 'message', oyente: (evento: MessageEvent) => void): void {
      this.oyentes.delete(oyente);
    }
    close(): void {
      canales.delete(this);
    }
  }
  const creados: CanalFalso[] = [];
  return {
    creados,
    fabrica: (nombre: string) => {
      const canal = new CanalFalso(nombre);
      creados.push(canal);
      return canal;
    },
  };
}

describe('crearCanalSesion', () => {
  it('avisa a la otra pestaña el cierre con su motivo, por el canal muelas-auth', () => {
    const hub = crearHubDeCanales();
    const pestanaA = crearCanalSesion(hub.fabrica);
    const pestanaB = crearCanalSesion(hub.fabrica);
    const recibidos: string[] = [];
    pestanaB.alRecibir((motivo) => recibidos.push(motivo));

    pestanaA.publicar('logout');
    pestanaA.publicar('contrasena-cambiada');

    expect(NOMBRE_CANAL).toBe('muelas-auth');
    expect(hub.creados.every((c) => c.name === 'muelas-auth')).toBe(true);
    expect(recibidos).toEqual(['logout', 'contrasena-cambiada']);
  });

  it('el mensaje lleva solo el tipo y el motivo, nunca el token ni datos del usuario', () => {
    const hub = crearHubDeCanales();
    const canal = crearCanalSesion(hub.fabrica);

    canal.publicar('logout');

    expect(hub.creados[0]?.enviados).toEqual([{ tipo: 'sesion-cerrada', motivo: 'logout' }]);
  });

  it('ignora mensajes con otro formato', () => {
    const hub = crearHubDeCanales();
    const canal = crearCanalSesion(hub.fabrica);
    const recibidos: string[] = [];
    canal.alRecibir((motivo) => recibidos.push(motivo));
    const intruso = hub.fabrica('muelas-auth');

    for (const mensaje of [null, 'sesion-cerrada', { tipo: 'sesion-cerrada' }, { tipo: 'sesion-cerrada', motivo: 'otro' }, { tipo: 'token', motivo: 'logout' }]) {
      intruso.postMessage(mensaje);
    }

    expect(recibidos).toEqual([]);
  });

  it('deja de escuchar al desuscribirse y al cerrarse', () => {
    const hub = crearHubDeCanales();
    const pestanaA = crearCanalSesion(hub.fabrica);
    const pestanaB = crearCanalSesion(hub.fabrica);
    const oyente = vi.fn();
    const desuscribir = pestanaB.alRecibir(oyente);

    desuscribir();
    pestanaA.publicar('logout');

    expect(oyente).not.toHaveBeenCalled();
  });

  it('sin BroadcastChannel no falla nada', () => {
    const canal = crearCanalSesion(null);

    expect(() => canal.publicar('logout')).not.toThrow();
    expect(() => canal.alRecibir(() => undefined)()).not.toThrow();
    expect(() => canal.cerrar()).not.toThrow();
  });
});

describe('esAvisoSesionCerrada', () => {
  it('acepta solo el formato exacto con un motivo conocido', () => {
    expect(esAvisoSesionCerrada({ tipo: 'sesion-cerrada', motivo: 'logout' })).toBe(true);
    expect(esAvisoSesionCerrada({ tipo: 'sesion-cerrada', motivo: 'contrasena-cambiada' })).toBe(true);
    expect(esAvisoSesionCerrada({ tipo: 'sesion-cerrada', motivo: 'vencida' })).toBe(false);
    expect(esAvisoSesionCerrada(undefined)).toBe(false);
  });
});
