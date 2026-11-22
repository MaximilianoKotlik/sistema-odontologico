import { describe, expect, it } from 'vitest';
import { mensajeDeError, segundosDeEspera, textoDeEspera, type PantallaAuth } from './errores';

// Tarea 1.3 de C-07 (design D8). Los textos salen de los mockups aprobados (#14, versión 3ffa2f7).
const AHORA = new Date('2026-11-21T15:00:00Z');

describe('segundosDeEspera', () => {
  it('lee Retry-After en segundos', () => {
    expect(segundosDeEspera('60', AHORA)).toBe(60);
    expect(segundosDeEspera('0', AHORA)).toBe(0);
  });

  it('lee Retry-After como fecha HTTP', () => {
    expect(segundosDeEspera('Sat, 21 Nov 2026 15:02:00 GMT', AHORA)).toBe(120);
  });

  it('redondea hacia arriba una fecha con fracción de segundo', () => {
    expect(segundosDeEspera('Sat, 21 Nov 2026 15:00:01 GMT', new Date('2026-11-21T15:00:00.500Z'))).toBe(1);
  });

  it('una fecha pasada da 0', () => {
    expect(segundosDeEspera('Sat, 21 Nov 2026 14:00:00 GMT', AHORA)).toBe(0);
  });

  it('sin encabezado o con un valor inválido devuelve null', () => {
    for (const malo of [null, '', 'pronto', '-5', '1.5']) {
      expect(segundosDeEspera(malo, AHORA), String(malo)).toBeNull();
    }
  });
});

describe('textoDeEspera', () => {
  it('muestra minutos redondeados hacia arriba, como en los mockups', () => {
    expect(textoDeEspera(60)).toBe('1 minuto');
    expect(textoDeEspera(30)).toBe('1 minuto');
    expect(textoDeEspera(61)).toBe('2 minutos');
    expect(textoDeEspera(2700)).toBe('45 minutos');
  });

  it('nunca muestra 0 minutos', () => {
    expect(textoDeEspera(0)).toBe('1 minuto');
  });
});

describe('mensajeDeError', () => {
  it('credenciales inválidas: aviso genérico sin campo (RN-AU-03)', () => {
    expect(mensajeDeError('CREDENCIALES_INVALIDAS', 'login')).toEqual({ campo: null, texto: 'Email o contraseña incorrectos.' });
  });

  it('cuenta bloqueada: aviso sin campo', () => {
    expect(mensajeDeError('CUENTA_BLOQUEADA', 'login')).toEqual({ campo: null, texto: 'Pausamos el ingreso por seguridad.' });
  });

  it('sesión expirada: aviso del login', () => {
    expect(mensajeDeError('SESION_EXPIRADA', 'login')).toEqual({ campo: null, texto: 'Tu sesión venció.' });
  });

  it('email en uso: en el campo email', () => {
    expect(mensajeDeError('EMAIL_EN_USO', 'registro')).toEqual({ campo: 'email', texto: 'Ya hay una cuenta con este email.' });
  });

  it('contraseña débil: en el campo de la contraseña nueva de cada pantalla', () => {
    expect(mensajeDeError('PASSWORD_DEBIL', 'registro').campo).toBe('password');
    expect(mensajeDeError('PASSWORD_DEBIL', 'recuperar-confirmar').campo).toBe('passwordNueva');
    expect(mensajeDeError('PASSWORD_DEBIL', 'cambiar-contrasena').campo).toBe('passwordNueva');
    expect(mensajeDeError('PASSWORD_DEBIL', 'registro').texto).toBe('Esa contraseña no se puede usar.');
  });

  it('contraseña actual inválida: en el campo de la contraseña actual', () => {
    expect(mensajeDeError('PASSWORD_ACTUAL_INVALIDA', 'cambiar-contrasena')).toEqual({
      campo: 'passwordActual',
      texto: 'La contraseña actual no es correcta.',
    });
  });

  it('token inválido: un solo mensaje para vencido, usado o revocado', () => {
    expect(mensajeDeError('TOKEN_INVALIDO', 'recuperar-confirmar')).toEqual({ campo: null, texto: 'Este link ya no sirve' });
  });

  it('términos no aceptados que igual llegan del backend: en la casilla de términos', () => {
    expect(mensajeDeError('TERMINOS_NO_ACEPTADOS', 'registro')).toEqual({
      campo: 'aceptaTerminos',
      texto: 'Tenés que aceptar los términos y la política de privacidad.',
    });
  });

  it('demasiadas solicitudes: el título depende de la pantalla', () => {
    const esperados: Record<PantallaAuth, string> = {
      login: 'Demasiados intentos desde esta conexión.',
      registro: 'Se crearon varias cuentas desde esta conexión.',
      recuperar: 'Ya pediste varios links.',
      'recuperar-confirmar': 'Hiciste varios intentos seguidos.',
      'cambiar-contrasena': 'Hiciste varios intentos seguidos.',
    };
    for (const [pantalla, texto] of Object.entries(esperados) as [PantallaAuth, string][]) {
      expect(mensajeDeError('DEMASIADAS_SOLICITUDES', pantalla), pantalla).toEqual({ campo: null, texto });
    }
  });

  it('sin conexión: aviso sin campo', () => {
    expect(mensajeDeError('SIN_CONEXION', 'login')).toEqual({ campo: null, texto: 'Sin conexión.' });
  });

  it('un código desconocido da un aviso genérico sin campo y sin mostrar el código', () => {
    for (const codigo of ['ORIGEN_NO_PERMITIDO', 'ERROR_INESPERADO', 'ALGO_NUEVO']) {
      const mensaje = mensajeDeError(codigo, 'login');
      expect(mensaje.campo, codigo).toBeNull();
      expect(mensaje.texto.length, codigo).toBeGreaterThan(0);
      expect(mensaje.texto, codigo).not.toContain(codigo);
    }
  });
});
