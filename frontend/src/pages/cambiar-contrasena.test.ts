import { describe, expect, it, vi } from 'vitest';
import { escribir, enviar, esperarPedidos } from '../features/auth/prueba-contexto';
import { crearClienteHttp } from '../shared/http';
import { pagina } from './cambiar-contrasena';
import type { ContextoPagina } from './pagina';

// Tarea 3.4 de C-07 (spec app-auth/contrasena, US-005; mockup `cambiar-contrasena.html`). Datos ficticios.

function montar(respuestas: (() => Response)[]) {
  const llamadas: { url: string; cuerpo: unknown }[] = [];
  const fetch = vi.fn(async (entrada: RequestInfo | URL, init: RequestInit = {}) => {
    llamadas.push({ url: String(entrada), cuerpo: typeof init.body === 'string' ? (JSON.parse(init.body) as unknown) : undefined });
    const siguiente = respuestas.shift();
    if (siguiente === undefined) throw new Error('Sin respuesta');
    return siguiente();
  });
  const http = crearClienteHttp({ fetch });
  http.establecerToken('token-ficticio');
  const contenedor = document.createElement('div');
  document.body.replaceChildren(contenedor);
  const terminar = vi.fn(async () => undefined);
  const contexto: ContextoPagina = {
    contenedor,
    acciones: document.createElement('div'),
    params: {},
    http,
    zonaHoraria: 'America/Argentina/Buenos_Aires',
    cuenta: { terminarPorCambioDeContrasena: terminar },
  };
  pagina.montar(contexto);
  const [actual, nueva, repetida] = contenedor.querySelectorAll<HTMLInputElement>('input[type="password"]');
  return { contenedor, llamadas, terminar, actual: actual ?? null, nueva: nueva ?? null, repetida: repetida ?? null };
}

const problema = (status: number, codigo: string) => () =>
  new Response(JSON.stringify({ status, codigo }), { status, headers: { 'Content-Type': 'application/problem+json' } });

describe('cambiar contraseña', () => {
  it('avisa antes de enviar que se cierra la sesión en todos los dispositivos, también en este', () => {
    const { contenedor } = montar([]);

    expect(pagina.titulo).toBe('Cambiar contraseña');
    expect(contenedor.textContent).toContain('todos tus dispositivos, también en este');
  });

  it('con 204 termina la sesión de todas las pestañas y lleva al login', async () => {
    const { contenedor, llamadas, terminar, actual, nueva, repetida } = montar([() => new Response(null, { status: 204 })]);
    escribir(actual, 'ficticia-actual-01');
    escribir(nueva, 'ficticia-nueva-01');
    escribir(repetida, 'ficticia-nueva-01');

    enviar(contenedor);
    await esperarPedidos();

    expect(llamadas[0]).toEqual({ url: '/api/v1/auth/cambiar-password', cuerpo: { passwordActual: 'ficticia-actual-01', passwordNueva: 'ficticia-nueva-01' } });
    expect(terminar).toHaveBeenCalledTimes(1);
  });

  it('contraseña actual incorrecta: en su campo, que se vacía', async () => {
    const { contenedor, terminar, actual, nueva, repetida } = montar([problema(400, 'PASSWORD_ACTUAL_INVALIDA')]);
    escribir(actual, 'ficticia-mal-01');
    escribir(nueva, 'ficticia-nueva-01');
    escribir(repetida, 'ficticia-nueva-01');

    enviar(contenedor);
    await esperarPedidos();

    expect(actual?.getAttribute('aria-invalid')).toBe('true');
    expect(actual?.value).toBe('');
    expect(contenedor.textContent).toContain('La contraseña actual no es correcta.');
    expect(terminar).not.toHaveBeenCalled();
  });

  it('contraseña débil: "Distinta de tu email" en falta y el campo inválido', async () => {
    const { contenedor, actual, nueva, repetida } = montar([problema(400, 'PASSWORD_DEBIL')]);
    escribir(actual, 'ficticia-actual-01');
    escribir(nueva, 'ficticia-nueva-01');
    escribir(repetida, 'ficticia-nueva-01');

    enviar(contenedor);
    await esperarPedidos();

    const distinta = [...contenedor.querySelectorAll<HTMLLIElement>('li')].find((li) => li.textContent?.startsWith('Distinta de tu email'));
    expect(distinta?.dataset['estado']).toBe('fallado');
    expect(nueva?.getAttribute('aria-invalid')).toBe('true');
  });

  it('si la repetición no coincide no envía', async () => {
    const { contenedor, llamadas, actual, nueva, repetida } = montar([]);
    escribir(actual, 'ficticia-actual-01');
    escribir(nueva, 'ficticia-nueva-01');
    escribir(repetida, 'ficticia-nueva-02');

    enviar(contenedor);
    await esperarPedidos();

    expect(llamadas).toHaveLength(0);
    expect(repetida?.getAttribute('aria-invalid')).toBe('true');
  });
});
