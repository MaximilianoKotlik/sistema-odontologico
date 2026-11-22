import { describe, expect, it, vi } from 'vitest';
import { ApiError, crearClienteHttp } from '../../shared/http';
import { cambiarPassword, confirmarRecuperacion, login, logout, pedirRecuperacion, registrar } from './api';

// Tarea 2.1 de C-07: las seis llamadas de auth sobre un doble de fetch. Datos ficticios evidentes.
interface Llamada {
  readonly url: string;
  readonly init: RequestInit;
}

function clienteCon(respuesta: () => Response) {
  const llamadas: Llamada[] = [];
  const falso = vi.fn(async (entrada: RequestInfo | URL, init: RequestInit = {}) => {
    llamadas.push({ url: String(entrada), init });
    return respuesta();
  });
  return { http: crearClienteHttp({ fetch: falso }), llamadas };
}

const json = (cuerpo: unknown, status = 200) => new Response(JSON.stringify(cuerpo), { status, headers: { 'Content-Type': 'application/json' } });
const vacio = () => new Response(null, { status: 204 });
const cuerpoDe = (llamada: Llamada | undefined): unknown => JSON.parse(String(llamada?.init.body));

describe('api de auth', () => {
  it('login manda email y contraseña y devuelve el access token', async () => {
    const { http, llamadas } = clienteCon(() => json({ accessToken: 'token-ficticio', expiraEn: 900 }));

    await expect(login(http, 'odontologa@example.com', 'ficticia-segura-01')).resolves.toBe('token-ficticio');

    expect(llamadas[0]?.url).toBe('/api/v1/auth/login');
    expect(llamadas[0]?.init.method).toBe('POST');
    expect(llamadas[0]?.init.credentials).toBe('include');
    expect(cuerpoDe(llamadas[0])).toEqual({ email: 'odontologa@example.com', password: 'ficticia-segura-01' });
  });

  it('login rechaza una respuesta sin token', async () => {
    const { http } = clienteCon(() => json({ expiraEn: 900 }));

    const error = await login(http, 'odontologa@example.com', 'ficticia-segura-01').catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ codigo: 'ERROR_INESPERADO' });
  });

  it('registrar manda los seis campos del contrato', async () => {
    const { http, llamadas } = clienteCon(() => json({ usuarioId: 'u', consultorioId: 'c', slug: 'consultorio-demo' }, 201));
    const datos = {
      nombre: 'Ana',
      apellido: 'Ejemplo',
      email: 'odontologa@example.com',
      password: 'ficticia-segura-01',
      nombreConsultorio: 'Consultorio Demo',
      aceptaTerminos: true,
    };

    await registrar(http, datos);

    expect(llamadas[0]?.url).toBe('/api/v1/auth/registro');
    expect(cuerpoDe(llamadas[0])).toEqual(datos);
  });

  it('pedirRecuperacion y confirmarRecuperacion usan sus rutas', async () => {
    const { http, llamadas } = clienteCon(vacio);

    await pedirRecuperacion(http, 'odontologa@example.com');
    await confirmarRecuperacion(http, 'token-de-recuperacion', 'ficticia-segura-01');

    expect(llamadas.map((l) => l.url)).toEqual(['/api/v1/auth/recuperacion', '/api/v1/auth/recuperacion/confirmar']);
    expect(cuerpoDe(llamadas[0])).toEqual({ email: 'odontologa@example.com' });
    expect(cuerpoDe(llamadas[1])).toEqual({ token: 'token-de-recuperacion', password: 'ficticia-segura-01' });
  });

  it('cambiarPassword manda la actual y la nueva con la sesión', async () => {
    const { http, llamadas } = clienteCon(vacio);
    http.establecerToken('token-ficticio');

    await cambiarPassword(http, 'ficticia-actual-01', 'ficticia-nueva-01');

    expect(llamadas[0]?.url).toBe('/api/v1/auth/cambiar-password');
    expect(new Headers(llamadas[0]?.init.headers).get('Authorization')).toBe('Bearer token-ficticio');
    expect(cuerpoDe(llamadas[0])).toEqual({ passwordActual: 'ficticia-actual-01', passwordNueva: 'ficticia-nueva-01' });
  });

  it('logout llama a su ruta sin cuerpo', async () => {
    const { http, llamadas } = clienteCon(vacio);

    await logout(http);

    expect(llamadas[0]?.url).toBe('/api/v1/auth/logout');
    expect(llamadas[0]?.init.body).toBeUndefined();
  });
});
