import { describe, expect, it } from 'vitest';
import { decidirAcceso, esRutaPrivada } from './sesion';

// Tarea 2.3 de C-07 (design D2, D3): qué hace la app con cada ruta según haya token en memoria o no.

describe('esRutaPrivada', () => {
  it('/app y todo lo que cuelga de /app/ es privado', () => {
    expect(esRutaPrivada('/app')).toBe(true);
    expect(esRutaPrivada('/app/agenda')).toBe(true);
    expect(esRutaPrivada('/app/cuenta/contrasena')).toBe(true);
  });

  it('lo demás no', () => {
    for (const ruta of ['/', '/login', '/registro', '/application', '/precios']) {
      expect(esRutaPrivada(ruta), ruta).toBe(false);
    }
  });
});

describe('decidirAcceso', () => {
  it('ruta privada con token: sigue', () => {
    expect(decidirAcceso('/app/agenda', true)).toEqual({ tipo: 'seguir' });
  });

  it('ruta privada sin token: intenta restaurar la sesión', () => {
    expect(decidirAcceso('/app/configuracion', false)).toEqual({ tipo: 'restaurar' });
  });

  it('login y registro con token en memoria llevan a /app', () => {
    expect(decidirAcceso('/login', true)).toEqual({ tipo: 'redirigir', a: '/app' });
    expect(decidirAcceso('/registro', true)).toEqual({ tipo: 'redirigir', a: '/app' });
  });

  it('login y registro sin token muestran el formulario, sin restaurar (D3, opción a)', () => {
    expect(decidirAcceso('/login', false)).toEqual({ tipo: 'seguir' });
    expect(decidirAcceso('/registro', false)).toEqual({ tipo: 'seguir' });
  });

  it('recuperar, términos y privacidad se ven con o sin sesión', () => {
    for (const ruta of ['/recuperar', '/terminos', '/privacidad']) {
      expect(decidirAcceso(ruta, false), ruta).toEqual({ tipo: 'seguir' });
      expect(decidirAcceso(ruta, true), ruta).toEqual({ tipo: 'seguir' });
    }
  });

  it('una ruta desconocida fuera de /app va al login sin sesión y a /app con sesión', () => {
    expect(decidirAcceso('/no-existe', false)).toEqual({ tipo: 'redirigir', a: '/login' });
    expect(decidirAcceso('/no-existe', true)).toEqual({ tipo: 'redirigir', a: '/app' });
  });
});
