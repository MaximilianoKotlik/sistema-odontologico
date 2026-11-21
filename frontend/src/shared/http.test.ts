import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, crearClienteHttp, type ClienteHttp } from './http';

type Respuesta = Response | Error;
interface Llamada {
  readonly url: string;
  readonly init: RequestInit;
}

/** Doble de `fetch` que responde en orden según la ruta y registra cada llamada. */
function crearFetchFalso(guion: Record<string, Respuesta[]>) {
  const llamadas: Llamada[] = [];
  const falso = vi.fn(async (entrada: RequestInfo | URL, init: RequestInit = {}) => {
    const url = String(entrada);
    llamadas.push({ url, init });
    const cola = guion[new URL(url, 'http://local').pathname];
    const siguiente = cola?.shift();
    if (siguiente === undefined) throw new Error(`Sin respuesta preparada para ${url}`);
    if (siguiente instanceof Error) throw siguiente;
    return siguiente;
  });
  return { falso, llamadas };
}

const json = (cuerpo: unknown, status = 200, extra: HeadersInit = {}) =>
  new Response(JSON.stringify(cuerpo), { status, headers: { 'Content-Type': 'application/json', ...extra } });
const problema = (status: number, cuerpo: Record<string, unknown>) =>
  new Response(JSON.stringify({ status, ...cuerpo }), { status, headers: { 'Content-Type': 'application/problem+json' } });
const noAutorizado = () => problema(401, { codigo: 'NO_AUTENTICADO', detail: 'Token vencido' });
const encabezado = (l: Llamada | undefined, nombre: string) => new Headers(l?.init.headers).get(nombre);

describe('cliente HTTP: errores Problem Details', () => {
  it('convierte un problem+json en ApiError con status, codigo y detail', async () => {
    const { falso } = crearFetchFalso({ '/api/v1/cuenta': [problema(409, { codigo: 'EMAIL_EN_USO', detail: 'Ese email ya tiene cuenta.' })] });
    const http = crearClienteHttp({ fetch: falso });

    const error = await http.post('/api/v1/cuenta', { email: 'odonto@example.com' }).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ status: 409, codigo: 'EMAIL_EN_USO', detail: 'Ese email ya tiene cuenta.' });
  });

  it('un error sin cuerpo usa un código propio del cliente', async () => {
    const { falso } = crearFetchFalso({ '/api/v1/agenda': [new Response(null, { status: 500 })] });
    const http = crearClienteHttp({ fetch: falso });

    await expect(http.get('/api/v1/agenda')).rejects.toMatchObject({ status: 500, codigo: 'ERROR_INESPERADO' });
  });

  it('una respuesta exitosa con JSON inválido se convierte en ApiError ERROR_INESPERADO', async () => {
    const roto = () => new Response('{"turnos": [', { status: 200, headers: { 'Content-Type': 'application/json' } });
    const { falso } = crearFetchFalso({ '/api/v1/agenda': [roto()] });
    const http = crearClienteHttp({ fetch: falso });

    const error = await http.get('/api/v1/agenda').catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ status: 200, codigo: 'ERROR_INESPERADO' });
  });

  it('un refresh con JSON inválido cuenta como sesión no renovable', async () => {
    const roto = new Response('no-es-json', { status: 200, headers: { 'Content-Type': 'application/json' } });
    const { falso } = crearFetchFalso({ '/api/v1/auth/refresh': [roto] });
    const alVencerSesion = vi.fn();
    const http = crearClienteHttp({ fetch: falso, alVencerSesion });

    await expect(http.renovarSesion()).resolves.toBe(false);
    expect(alVencerSesion).toHaveBeenCalledTimes(1);
  });

  it('si fetch falla por falta de red, el error tiene codigo SIN_CONEXION', async () => {
    const { falso } = crearFetchFalso({ '/api/v1/agenda': [new TypeError('Failed to fetch')] });
    const http = crearClienteHttp({ fetch: falso });

    await expect(http.get('/api/v1/agenda')).rejects.toMatchObject({ status: 0, codigo: 'SIN_CONEXION' });
  });

  it('devuelve el JSON de una respuesta exitosa y undefined en un 204', async () => {
    const { falso } = crearFetchFalso({
      '/api/v1/agenda': [json({ turnos: [] })],
      '/api/v1/bloqueos/1': [new Response(null, { status: 204 })],
    });
    const http = crearClienteHttp({ fetch: falso });

    await expect(http.get('/api/v1/agenda')).resolves.toEqual({ turnos: [] });
    await expect(http.delete('/api/v1/bloqueos/1')).resolves.toBeUndefined();
  });
});

describe('cliente HTTP: token solo en memoria', () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
  });

  it('envía Authorization en pedidos privados', async () => {
    const { falso, llamadas } = crearFetchFalso({ '/api/v1/agenda': [json({})] });
    const http = crearClienteHttp({ fetch: falso });
    http.establecerToken('token-1');

    await http.get('/api/v1/agenda');

    expect(encabezado(llamadas[0], 'Authorization')).toBe('Bearer token-1');
  });

  it('no envía Authorization a la API pública ni a auth, y manda cookies a auth', async () => {
    const { falso, llamadas } = crearFetchFalso({
      '/api/v1/public/consultorios/demo': [json({})],
      '/api/v1/auth/logout': [new Response(null, { status: 204 })],
    });
    const http = crearClienteHttp({ fetch: falso });
    http.establecerToken('token-1');

    await http.get('/api/v1/public/consultorios/demo');
    await http.post('/api/v1/auth/logout');

    expect(encabezado(llamadas[0], 'Authorization')).toBeNull();
    expect(encabezado(llamadas[1], 'Authorization')).toBeNull();
    expect(llamadas[1]?.init.credentials).toBe('include');
  });

  it('no escribe el token en ningún almacenamiento del navegador', async () => {
    const { falso } = crearFetchFalso({ '/api/v1/auth/refresh': [json({ accessToken: 'token-nuevo' })] });
    const http = crearClienteHttp({ fetch: falso });

    http.establecerToken('token-1');
    await http.renovarSesion();

    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
    expect(document.cookie).toBe('');
    expect(http.tieneToken()).toBe(true);
  });

  it('serializa el cuerpo como JSON', async () => {
    const { falso, llamadas } = crearFetchFalso({ '/api/v1/bloqueos': [json({ id: 1 }, 201)] });
    const http = crearClienteHttp({ fetch: falso });

    await http.post('/api/v1/bloqueos', { motivo: 'Almuerzo' });

    expect(llamadas[0]?.init.body).toBe('{"motivo":"Almuerzo"}');
    expect(encabezado(llamadas[0], 'Content-Type')).toBe('application/json');
  });
});

describe('cliente HTTP: renovación ante 401', () => {
  let alVencerSesion: () => void;
  beforeEach(() => {
    alVencerSesion = vi.fn<() => void>();
  });

  it('token vencido: renueva una vez y reintenta con el token nuevo', async () => {
    const { falso, llamadas } = crearFetchFalso({
      '/api/v1/agenda': [noAutorizado(), json({ turnos: ['ok'] })],
      '/api/v1/auth/refresh': [json({ accessToken: 'token-2' })],
    });
    const http = crearClienteHttp({ fetch: falso, alVencerSesion });
    http.establecerToken('token-1');

    await expect(http.get('/api/v1/agenda')).resolves.toEqual({ turnos: ['ok'] });

    expect(llamadas.map((l) => new URL(l.url, 'http://local').pathname)).toEqual(['/api/v1/agenda', '/api/v1/auth/refresh', '/api/v1/agenda']);
    expect(llamadas[1]?.init).toMatchObject({ method: 'POST', credentials: 'include' });
    expect(encabezado(llamadas[2], 'Authorization')).toBe('Bearer token-2');
    expect(alVencerSesion).not.toHaveBeenCalled();
  });

  it('sin token en memoria (recarga), el 401 también dispara la renovación', async () => {
    const { falso, llamadas } = crearFetchFalso({
      '/api/v1/agenda': [noAutorizado(), json({})],
      '/api/v1/auth/refresh': [json({ accessToken: 'token-recuperado' })],
    });
    const http = crearClienteHttp({ fetch: falso });

    await http.get('/api/v1/agenda');

    expect(encabezado(llamadas[0], 'Authorization')).toBeNull();
    expect(encabezado(llamadas[2], 'Authorization')).toBe('Bearer token-recuperado');
  });

  it('si el reintento vuelve a dar 401, no renueva otra vez y vence la sesión', async () => {
    const { falso, llamadas } = crearFetchFalso({
      '/api/v1/agenda': [noAutorizado(), noAutorizado()],
      '/api/v1/auth/refresh': [json({ accessToken: 'token-2' })],
    });
    const http = crearClienteHttp({ fetch: falso, alVencerSesion });
    http.establecerToken('token-1');

    await expect(http.get('/api/v1/agenda')).rejects.toMatchObject({ status: 401, codigo: 'SESION_VENCIDA' });

    expect(llamadas.filter((l) => l.url.endsWith('/auth/refresh'))).toHaveLength(1);
    expect(alVencerSesion).toHaveBeenCalledTimes(1);
    expect(http.tieneToken()).toBe(false);
  });

  it('tres pedidos simultáneos con 401 comparten un único refresh', async () => {
    const { falso, llamadas } = crearFetchFalso({
      '/api/v1/agenda': [noAutorizado(), json({ n: 1 })],
      '/api/v1/bloqueos': [noAutorizado(), json({ n: 2 })],
      '/api/v1/configuracion': [noAutorizado(), json({ n: 3 })],
      '/api/v1/auth/refresh': [json({ accessToken: 'token-2' })],
    });
    const http = crearClienteHttp({ fetch: falso });
    http.establecerToken('token-1');

    const resultados = await Promise.all([http.get('/api/v1/agenda'), http.get('/api/v1/bloqueos'), http.get('/api/v1/configuracion')]);

    expect(resultados).toEqual([{ n: 1 }, { n: 2 }, { n: 3 }]);
    expect(llamadas.filter((l) => l.url.endsWith('/auth/refresh'))).toHaveLength(1);
    const reintentos = llamadas.filter((l) => !l.url.endsWith('/auth/refresh')).slice(3);
    expect(reintentos.map((l) => encabezado(l, 'Authorization'))).toEqual(['Bearer token-2', 'Bearer token-2', 'Bearer token-2']);
  });

  it('refresh rechazado: borra el token, avisa la sesión vencida y no reintenta', async () => {
    const { falso, llamadas } = crearFetchFalso({
      '/api/v1/agenda': [noAutorizado()],
      '/api/v1/auth/refresh': [problema(401, { codigo: 'REFRESH_INVALIDO' })],
    });
    const http = crearClienteHttp({ fetch: falso, alVencerSesion });
    http.establecerToken('token-1');

    await expect(http.get('/api/v1/agenda')).rejects.toMatchObject({ codigo: 'SESION_VENCIDA' });

    expect(http.tieneToken()).toBe(false);
    expect(alVencerSesion).toHaveBeenCalledTimes(1);
    expect(llamadas).toHaveLength(2);
  });

  it('con pedidos simultáneos y refresh rechazado, avisa la sesión vencida una sola vez', async () => {
    const { falso } = crearFetchFalso({
      '/api/v1/agenda': [noAutorizado()],
      '/api/v1/bloqueos': [noAutorizado()],
      '/api/v1/auth/refresh': [problema(401, { codigo: 'REFRESH_INVALIDO' })],
    });
    const http = crearClienteHttp({ fetch: falso, alVencerSesion });
    http.establecerToken('token-1');

    const resultados = await Promise.allSettled([http.get('/api/v1/agenda'), http.get('/api/v1/bloqueos')]);

    expect(resultados.map((r) => r.status)).toEqual(['rejected', 'rejected']);
    expect(alVencerSesion).toHaveBeenCalledTimes(1);
  });

  it('un refresh que falla por falta de red no vence la sesión', async () => {
    const { falso } = crearFetchFalso({
      '/api/v1/agenda': [noAutorizado()],
      '/api/v1/auth/refresh': [new TypeError('Failed to fetch')],
    });
    const http = crearClienteHttp({ fetch: falso, alVencerSesion });
    http.establecerToken('token-1');

    await expect(http.get('/api/v1/agenda')).rejects.toMatchObject({ codigo: 'SIN_CONEXION' });
    expect(alVencerSesion).not.toHaveBeenCalled();
  });

  it('un 401 de auth (p. ej. login fallido) no dispara renovación', async () => {
    const { falso, llamadas } = crearFetchFalso({ '/api/v1/auth/login': [problema(401, { codigo: 'CREDENCIALES_INVALIDAS' })] });
    const http = crearClienteHttp({ fetch: falso, alVencerSesion });

    await expect(http.post('/api/v1/auth/login', {})).rejects.toMatchObject({ codigo: 'CREDENCIALES_INVALIDAS' });
    expect(llamadas).toHaveLength(1);
    expect(alVencerSesion).not.toHaveBeenCalled();
  });
});

describe('cliente HTTP: aviso de falta de red', () => {
  it('avisa cuando un pedido falla por falta de red (navigator.onLine no es confiable)', async () => {
    const { falso } = crearFetchFalso({ '/api/v1/agenda': [new TypeError('Failed to fetch')] });
    const alFallarRed = vi.fn<() => void>();
    const http = crearClienteHttp({ fetch: falso, alFallarRed });

    await http.get('/api/v1/agenda').catch(() => undefined);

    expect(alFallarRed).toHaveBeenCalledTimes(1);
  });

  it('no avisa falta de red ante un error HTTP del servidor', async () => {
    const { falso } = crearFetchFalso({ '/api/v1/agenda': [new Response(null, { status: 500 })] });
    const alFallarRed = vi.fn<() => void>();
    const http = crearClienteHttp({ fetch: falso, alFallarRed });

    await http.get('/api/v1/agenda').catch(() => undefined);

    expect(alFallarRed).not.toHaveBeenCalled();
  });
});

describe('cliente HTTP: observación de respuestas', () => {
  it('avisa cada respuesta exitosa con su ruta (para la hora de la última agenda)', async () => {
    const fecha = 'Tue, 24 Nov 2026 13:32:00 GMT';
    const { falso } = crearFetchFalso({ '/api/v1/agenda': [json({}, 200, { Date: fecha })] });
    const alResponder = vi.fn<(ruta: string, respuesta: Response) => void>();
    const http: ClienteHttp = crearClienteHttp({ fetch: falso, alResponder });

    await http.get('/api/v1/agenda?dia=2026-11-24');

    expect(alResponder).toHaveBeenCalledWith('/api/v1/agenda?dia=2026-11-24', expect.any(Response));
    expect(alResponder.mock.calls[0]?.[1].headers.get('Date')).toBe(fecha);
  });
});
