import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  crearContextoDePrueba,
  enviar,
  escribir,
  esperarPedidos,
  problemaJson,
  sinContenido,
} from '../features/auth/prueba-contexto';
import { pagina } from './recuperar';

// Tarea 3.3 de C-07 (spec app-auth/contrasena, design D7; mockups `recuperar*.html`). Datos ficticios.
const EMAIL = 'odontologa@example.com';

afterEach(() => {
  vi.restoreAllMocks();
  history.replaceState(null, '', '/');
});

function montar(url: string, ...argumentos: Parameters<typeof crearContextoDePrueba>) {
  history.replaceState(null, '', url);
  const prueba = crearContextoDePrueba(...argumentos);
  pagina.montar(prueba.contexto);
  return prueba;
}

describe('pedido de recuperación', () => {
  it('sin token muestra el pedido y, con 202, el mismo mensaje exista o no la cuenta', async () => {
    const { contenedor, llamadas } = montar('/recuperar', { '/api/v1/auth/recuperacion': [() => new Response(null, { status: 202 })] });
    expect(contenedor.querySelector('h1')?.textContent).toBe('Restablecé tu contraseña');

    escribir(contenedor.querySelector('input[type="email"]'), EMAIL);
    enviar(contenedor);
    await esperarPedidos();

    expect(llamadas[0]?.cuerpo).toEqual({ email: EMAIL });
    const estado = contenedor.querySelector('[role="status"]');
    expect(estado?.querySelector('h1')?.textContent).toBe('Revisá tu email');
    expect(estado?.textContent).toContain(`Si hay una cuenta con ${EMAIL}, te llega un link`);
  });

  it('demasiados pedidos: aviso con la espera', async () => {
    const { contenedor } = montar('/recuperar', {
      '/api/v1/auth/recuperacion': [problemaJson(429, 'DEMASIADAS_SOLICITUDES', {}, { 'Retry-After': '2400' })],
    });
    escribir(contenedor.querySelector('input[type="email"]'), EMAIL);

    enviar(contenedor);
    await esperarPedidos();

    expect(contenedor.querySelector('[role="alert"]')?.textContent).toContain('Ya pediste varios links.');
    expect(contenedor.querySelector('[role="alert"]')?.textContent).toContain('40 minutos');
  });
});

describe('contraseña nueva desde el link', () => {
  it('quita el token de la URL antes de mostrar el formulario (D7)', () => {
    const reemplazar = vi.spyOn(history, 'replaceState');

    const { contenedor } = montar('/recuperar#token=token-de-prueba');

    expect(location.pathname + location.hash).toBe('/recuperar');
    expect(reemplazar).toHaveBeenCalledWith(null, '', '/recuperar');
    expect(contenedor.querySelector('h1')?.textContent).toBe('Creá tu contraseña nueva');
    expect(contenedor.textContent).not.toContain('token-de-prueba');
  });

  it('si las contraseñas no coinciden no envía y marca la repetición', async () => {
    const { contenedor, llamadas } = montar('/recuperar#token=token-de-prueba');
    const [nueva, repetida] = contenedor.querySelectorAll<HTMLInputElement>('input[type="password"]');
    escribir(nueva ?? null, 'ficticia-segura-01');
    escribir(repetida ?? null, 'ficticia-segura-02');

    enviar(contenedor);
    await esperarPedidos();

    expect(llamadas).toHaveLength(0);
    expect(repetida?.getAttribute('aria-invalid')).toBe('true');
    expect(contenedor.textContent).toContain('Las dos contraseñas no coinciden.');
  });

  it('con 204 lleva al login con el aviso de contraseña cambiada', async () => {
    const { contenedor, llamadas, contexto } = montar('/recuperar#token=token-de-prueba', {
      '/api/v1/auth/recuperacion/confirmar': [sinContenido()],
    });
    const [nueva, repetida] = contenedor.querySelectorAll<HTMLInputElement>('input[type="password"]');
    escribir(nueva ?? null, 'ficticia-segura-01');
    escribir(repetida ?? null, 'ficticia-segura-01');

    enviar(contenedor);
    await esperarPedidos();

    expect(llamadas[0]?.cuerpo).toEqual({ token: 'token-de-prueba', password: 'ficticia-segura-01' });
    expect(contexto.dejarAviso).toHaveBeenCalledWith({ tipo: 'contrasena-cambiada' });
    expect(contexto.navegar).toHaveBeenCalledWith('/login');
  });

  it('contraseña débil: conserva el token para reintentar', async () => {
    const { contenedor, llamadas } = montar('/recuperar#token=token-de-prueba', {
      '/api/v1/auth/recuperacion/confirmar': [problemaJson(400, 'PASSWORD_DEBIL'), sinContenido()],
    });
    const [nueva, repetida] = contenedor.querySelectorAll<HTMLInputElement>('input[type="password"]');
    escribir(nueva ?? null, 'ficticia-segura-01');
    escribir(repetida ?? null, 'ficticia-segura-01');

    enviar(contenedor);
    await esperarPedidos();
    expect(contenedor.querySelector('[role="alert"]')?.textContent).toContain('Esa contraseña no se puede usar.');
    expect(nueva?.getAttribute('aria-invalid')).toBe('true');

    escribir(nueva ?? null, 'ficticia-segura-02');
    escribir(repetida ?? null, 'ficticia-segura-02');
    enviar(contenedor);
    await esperarPedidos();

    expect(llamadas[1]?.cuerpo).toEqual({ token: 'token-de-prueba', password: 'ficticia-segura-02' });
  });

  it('token inválido: "Este link ya no sirve" con "Pedir un link nuevo"', async () => {
    const { contenedor } = montar('/recuperar#token=token-de-prueba', {
      '/api/v1/auth/recuperacion/confirmar': [problemaJson(400, 'TOKEN_INVALIDO')],
    });
    const [nueva, repetida] = contenedor.querySelectorAll<HTMLInputElement>('input[type="password"]');
    escribir(nueva ?? null, 'ficticia-segura-01');
    escribir(repetida ?? null, 'ficticia-segura-01');

    enviar(contenedor);
    await esperarPedidos();

    const alerta = contenedor.querySelector('[role="alert"]');
    expect(alerta?.querySelector('h1')?.textContent).toBe('Este link ya no sirve');
    expect(alerta?.querySelector('a[href="/recuperar"]')?.textContent).toBe('Pedir un link nuevo');
  });
});
