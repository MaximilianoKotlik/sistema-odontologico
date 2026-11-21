import type { components } from '../../shared/api-types';
import { ApiError, type ClienteHttp } from '../../shared/http';

/**
 * Las seis llamadas de auth (C-07, design D1) sobre los contratos de C-04 y C-05. El access token que devuelve el
 * login vuelve a quien llama y entra a memoria por `app.iniciarSesion` (D4): este módulo no lo guarda.
 */

type RegistroRequest = components['schemas']['RegistroRequest'];

export interface DatosRegistroApi {
  readonly nombre: string;
  readonly apellido: string;
  readonly email: string;
  readonly password: string;
  readonly nombreConsultorio: string;
  readonly aceptaTerminos: boolean;
}

function tieneAccessToken(valor: unknown): valor is { accessToken: string } {
  return typeof valor === 'object' && valor !== null && typeof (valor as Record<string, unknown>)['accessToken'] === 'string' && (valor as Record<string, unknown>)['accessToken'] !== '';
}

export async function login(http: ClienteHttp, email: string, password: string): Promise<string> {
  const respuesta = await http.post<unknown>('/api/v1/auth/login', { email, password });
  if (!tieneAccessToken(respuesta)) throw new ApiError(200, 'ERROR_INESPERADO', 'La respuesta del login no trae el token.');
  return respuesta.accessToken;
}

export async function registrar(http: ClienteHttp, datos: DatosRegistroApi): Promise<void> {
  const cuerpo: RegistroRequest = { ...datos };
  await http.post('/api/v1/auth/registro', cuerpo);
}

export async function pedirRecuperacion(http: ClienteHttp, email: string): Promise<void> {
  await http.post('/api/v1/auth/recuperacion', { email });
}

export async function confirmarRecuperacion(http: ClienteHttp, token: string, password: string): Promise<void> {
  await http.post('/api/v1/auth/recuperacion/confirmar', { token, password });
}

export async function cambiarPassword(http: ClienteHttp, passwordActual: string, passwordNueva: string): Promise<void> {
  await http.post('/api/v1/auth/cambiar-password', { passwordActual, passwordNueva });
}

export async function logout(http: ClienteHttp): Promise<void> {
  await http.post('/api/v1/auth/logout');
}
