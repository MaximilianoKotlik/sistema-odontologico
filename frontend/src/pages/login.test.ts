import { describe, expect, it } from 'vitest';
import {
  crearContextoDePrueba,
  enviar,
  escribir,
  esperarPedidos,
  problemaJson,
  respuestaJson,
} from '../features/auth/prueba-contexto';
import { pagina } from './login';

// Tarea 3.1 de C-07: login con los estados del mockup aprobado (spec app-auth/acceso). Datos ficticios.
const EMAIL = 'odontologa@example.com';

function montar(...argumentos: Parameters<typeof crearContextoDePrueba>) {
  const prueba = crearContextoDePrueba(...argumentos);
  pagina.montar(prueba.contexto);
  const email = prueba.contenedor.querySelector<HTMLInputElement>('input[type="email"]');
  const password = prueba.contenedor.querySelector<HTMLInputElement>('input[autocomplete="current-password"]');
  const boton = () => prueba.contenedor.querySelector<HTMLButtonElement>('button[type="submit"]');
  return { ...prueba, email, password, boton };
}

describe('login', () => {
  it('muestra el formulario accesible del mockup', () => {
    const { contenedor, email, password } = montar();

    expect(contenedor.querySelectorAll('h1')).toHaveLength(1);
    expect(contenedor.querySelector('h1')?.textContent).toBe('Ingresá a tu agenda');
    expect(email?.getAttribute('autocomplete')).toBe('username');
    expect(password?.type).toBe('password');
    expect(contenedor.querySelector('a[href="/recuperar"]')?.textContent).toBe('¿La olvidaste?');
    expect(contenedor.querySelector('a[href="/registro"]')?.textContent).toBe('Probá gratis 30 días');
  });

  it('mostrar u ocultar la contraseña es un botón con aria-pressed', () => {
    const { contenedor, password } = montar();
    const alternar = contenedor.querySelector<HTMLButtonElement>('button[aria-pressed]');

    alternar?.click();

    expect(alternar?.getAttribute('aria-pressed')).toBe('true');
    expect(password?.type).toBe('text');
    alternar?.click();
    expect(password?.type).toBe('password');
  });

  it('con credenciales válidas entra con el token y el volver de la URL', async () => {
    const { contexto, email, password, llamadas, contenedor } = montar(
      { '/api/v1/auth/login': [respuestaJson({ accessToken: 'token-ficticio', expiraEn: 900 })] },
      { query: 'volver=/app/configuracion' },
    );
    escribir(email, EMAIL);
    escribir(password, 'ficticia-segura-01');

    enviar(contenedor);
    await esperarPedidos();

    expect(llamadas[0]?.cuerpo).toEqual({ email: EMAIL, password: 'ficticia-segura-01' });
    expect(contexto.entrar).toHaveBeenCalledWith('token-ficticio', '/app/configuracion');
  });

  it('mientras envía, el botón queda deshabilitado con "Ingresando…"', async () => {
    let responder: (r: Response) => void = () => undefined;
    const { email, password, contenedor, boton } = montar({
      '/api/v1/auth/login': [() => new Promise<Response>((r) => (responder = r))],
    });
    escribir(email, EMAIL);
    escribir(password, 'ficticia-segura-01');

    enviar(contenedor);
    await esperarPedidos();

    expect(boton()?.disabled).toBe(true);
    expect(boton()?.textContent).toContain('Ingresando…');
    responder(new Response(JSON.stringify({ accessToken: 't' }), { headers: { 'Content-Type': 'application/json' } }));
  });

  it('con campos vacíos no envía nada', async () => {
    const { contenedor, llamadas, email } = montar();

    enviar(contenedor);
    await esperarPedidos();

    expect(llamadas).toHaveLength(0);
    expect(email?.getAttribute('aria-invalid')).toBe('true');
  });

  it('credenciales inválidas: aviso genérico, contraseña vacía y email conservado', async () => {
    const { email, password, contenedor } = montar({ '/api/v1/auth/login': [problemaJson(401, 'CREDENCIALES_INVALIDAS')] });
    escribir(email, EMAIL);
    escribir(password, 'ficticia-incorrecta');

    enviar(contenedor);
    await esperarPedidos();

    const alerta = contenedor.querySelector('[role="alert"]');
    expect(alerta?.textContent).toContain('Email o contraseña incorrectos.');
    expect(password?.value).toBe('');
    expect(email?.value).toBe(EMAIL);
    expect(email?.getAttribute('aria-invalid')).toBeNull();
  });

  it('cuenta bloqueada: minutos restantes y link para restablecer', async () => {
    const { email, password, contenedor } = montar({ '/api/v1/auth/login': [problemaJson(423, 'CUENTA_BLOQUEADA', { minutosRestantes: 12 })] });
    escribir(email, EMAIL);
    escribir(password, 'ficticia-segura-01');

    enviar(contenedor);
    await esperarPedidos();

    const alerta = contenedor.querySelector('[role="alert"]');
    expect(alerta?.textContent).toContain('Pausamos el ingreso por seguridad.');
    expect(alerta?.textContent).toContain('12 minutos');
    expect(alerta?.querySelector('a[href="/recuperar"]')?.textContent).toBe('restablecela');
  });

  it('demasiados intentos: espera desde Retry-After y envío deshabilitado', async () => {
    const { email, password, contenedor, boton } = montar({
      '/api/v1/auth/login': [problemaJson(429, 'DEMASIADAS_SOLICITUDES', {}, { 'Retry-After': '60' })],
    });
    escribir(email, EMAIL);
    escribir(password, 'ficticia-segura-01');

    enviar(contenedor);
    await esperarPedidos();

    expect(contenedor.querySelector('[role="alert"]')?.textContent).toContain('Esperá 1 minuto y volvé a intentar.');
    expect(boton()?.disabled).toBe(true);
  });

  it('sin conexión: aviso y "Ingresar" deshabilitado, y se habilita al volver la red', () => {
    const { contenedor, boton, conectividad } = montar({}, { online: false });

    expect(contenedor.textContent).toContain('Para ingresar necesitás internet.');
    expect(boton()?.disabled).toBe(true);

    conectividad.set({ online: true });
    expect(boton()?.disabled).toBe(false);
  });

  it('avisa por qué se llegó: sesión vencida, cerrada y contraseña cambiada', () => {
    expect(montar({}, { aviso: { tipo: 'sesion-vencida' } }).contenedor.querySelector('[role="status"]')?.textContent).toContain('Tu sesión venció.');
    expect(montar({}, { aviso: { tipo: 'sesion-cerrada' } }).contenedor.querySelector('[role="status"]')?.textContent).toContain('Cerraste sesión.');
    expect(montar({}, { aviso: { tipo: 'contrasena-cambiada' } }).contenedor.querySelector('[role="status"]')?.textContent).toContain('Cambiaste tu contraseña.');
  });

  it('después de un registro sin login automático, trae el email cargado', () => {
    const { contenedor, email } = montar({}, { aviso: { tipo: 'registro-sin-login', email: EMAIL } });

    expect(contenedor.querySelector('[role="status"]')?.textContent).toContain('¡Tu consultorio está listo!');
    expect(email?.value).toBe(EMAIL);
  });
});
