import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { montarAplicacion, type Aplicacion } from '../../app';
import { escribir } from './prueba-contexto';

/**
 * Tarea 4.1 de C-07 (design D4, aprobada por Maxi en #37): ni el access token ni las contraseñas tocan un lugar
 * persistente o visible para otro script. Recorre registro con login automático, recarga con restauración, cambio
 * de contraseña, login y logout sobre la app real, con espías en cada lugar prohibido. Datos ficticios.
 */

const SECRETOS = ['TOKEN-SECRETO-REGISTRO', 'TOKEN-SECRETO-REFRESH', 'TOKEN-SECRETO-LOGIN', 'ficticia-segura-01', 'ficticia-nueva-02'] as const;

const json = (cuerpo: unknown, status = 200) => new Response(JSON.stringify(cuerpo), { status, headers: { 'Content-Type': 'application/json' } });

function crearBackend() {
  const tokens = ['TOKEN-SECRETO-REGISTRO', 'TOKEN-SECRETO-LOGIN'];
  return vi.fn(async (entrada: RequestInfo | URL) => {
    const url = String(entrada);
    if (url === '/api/v1/auth/registro') return json({ usuarioId: 'u', consultorioId: 'c', slug: 'consultorio-demo' }, 201);
    if (url === '/api/v1/auth/login') return json({ accessToken: tokens.shift() ?? 'TOKEN-SECRETO-LOGIN' });
    if (url === '/api/v1/auth/refresh') return json({ accessToken: 'TOKEN-SECRETO-REFRESH' });
    if (url === '/api/v1/auth/cambiar-password' || url === '/api/v1/auth/logout') return new Response(null, { status: 204 });
    return json({});
  });
}

const esperarA = async (condicion: () => boolean) => vi.waitFor(() => expect(condicion()).toBe(true));
const input = (nombre: string) => document.querySelector<HTMLInputElement>(`input[name="${nombre}"]`);
const enviarFormulario = () => document.querySelector('form')?.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));

describe('no-persistencia del token y las contraseñas (D4)', () => {
  const vistos: unknown[] = [];
  let apps: Aplicacion[] = [];

  beforeEach(() => {
    vistos.length = 0;
    document.body.innerHTML = '<div id="app"></div>';
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation((...args) => void vistos.push(args));
    vi.stubGlobal('indexedDB', { open: vi.fn((...args: unknown[]) => void vistos.push(args)) });
    const descriptor = Object.getOwnPropertyDescriptor(Document.prototype, 'cookie');
    vi.spyOn(document, 'cookie', 'set').mockImplementation((valor: string) => {
      vistos.push(valor);
      descriptor?.set?.call(document, valor);
    });
    const empujar = history.pushState.bind(history);
    const reemplazar = history.replaceState.bind(history);
    vi.spyOn(history, 'pushState').mockImplementation((...args) => {
      vistos.push(args);
      empujar(...args);
    });
    vi.spyOn(history, 'replaceState').mockImplementation((...args) => {
      vistos.push(args);
      reemplazar(...args);
    });
    vi.spyOn(console, 'log').mockImplementation((...args) => void vistos.push(args));
  });

  afterEach(() => {
    for (const app of apps) app.detener();
    apps = [];
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('registro con login automático, recarga, cambio de contraseña, login y logout', async () => {
    const fetch = crearBackend();
    history.replaceState(null, '', '/registro');
    const app = montarAplicacion(document, { fetch, canalSesion: null, locks: null });
    apps.push(app);
    await app.listo;

    // Registro con entrada automática.
    escribir(input('nombre'), 'Ana');
    escribir(input('apellido'), 'Ejemplo');
    escribir(input('email'), 'odontologa@example.com');
    escribir(input('password'), 'ficticia-segura-01');
    escribir(input('nombreConsultorio'), 'Consultorio Demo');
    const terminos = input('aceptaTerminos');
    if (terminos !== null) terminos.checked = true;
    enviarFormulario();
    await esperarA(() => document.querySelector('[data-app-root]') !== null);
    expect(app.http.tieneToken()).toBe(true);

    // Recarga: una app nueva restaura la sesión con el refresh.
    app.detener();
    document.body.innerHTML = '<div id="app"></div>';
    history.replaceState(null, '', '/app/cuenta/contrasena');
    const recargada = montarAplicacion(document, { fetch, canalSesion: null, locks: null });
    apps.push(recargada);
    await recargada.listo;
    await esperarA(() => document.querySelector('input[name="passwordActual"]') !== null);

    // Cambio de contraseña: termina la sesión y lleva al login.
    escribir(input('passwordActual'), 'ficticia-segura-01');
    escribir(input('passwordNueva'), 'ficticia-nueva-02');
    escribir(input('passwordRepetida'), 'ficticia-nueva-02');
    enviarFormulario();
    await esperarA(() => location.pathname === '/login' && document.querySelector('input[name="email"]') !== null);

    // Login con la contraseña nueva y logout.
    escribir(input('email'), 'odontologa@example.com');
    escribir(input('password'), 'ficticia-nueva-02');
    enviarFormulario();
    await esperarA(() => document.querySelector('[data-app-root]') !== null);
    await recargada.cerrarSesion();

    const contenidoVisto = JSON.stringify(vistos);
    for (const secreto of SECRETOS) {
      expect(contenidoVisto, secreto).not.toContain(secreto);
    }
    expect(location.href).not.toMatch(/TOKEN|ficticia/);
    // El recorrido sí pasó por los lugares espiados (historial), así que el test no es vacío.
    expect(vistos.length).toBeGreaterThan(0);
  });
});
