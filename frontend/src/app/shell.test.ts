import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { crearStore } from '../shared/store';
import type { EstadoConectividad } from '../shared/conectividad';
import { boton } from '../shared/ui/boton';
import { montarShell, type Shell } from './shell';

const ZONA = 'America/Argentina/Buenos_Aires';

function nombre(e: Element): string {
  const copia = e.cloneNode(true) as Element;
  copia.querySelectorAll('[aria-hidden="true"]').forEach((n) => n.remove());
  return (copia.textContent ?? '').replace(/\s+/g, ' ').trim();
}

function preparar(marca = 'Turnos Odonto (TBD)') {
  document.body.innerHTML = '<div id="app"></div>';
  const anfitrion = document.querySelector<HTMLElement>('#app');
  if (anfitrion === null) throw new Error('falta #app');
  const conectividad = crearStore<EstadoConectividad>({ online: true, ultimaSincronizacion: null });
  const alCerrarSesion = vi.fn();
  const shell: Shell = montarShell(anfitrion, { marca, conectividad, zonaHoraria: () => ZONA, alCerrarSesion });
  montados.push(shell);
  return { shell, conectividad, alCerrarSesion };
}

const montados: Shell[] = [];
const navInferior = () => document.querySelector<HTMLElement>('[data-nav-inferior]');
const navLateral = () => document.querySelector<HTMLElement>('[data-nav-lateral]');

beforeEach(() => {
  document.title = '';
});
afterEach(() => {
  montados.splice(0).forEach((s) => s.destruir());
});

describe('shell: estructura y accesibilidad', () => {
  it('el primer elemento enfocable es "Saltar al contenido" y apunta al contenido', () => {
    const { shell } = preparar();
    const primero = document.querySelector('#app a, #app button');
    expect(nombre(primero ?? document.body)).toBe('Saltar al contenido');
    expect(primero?.getAttribute('href')).toBe('#contenido');
    expect(shell.contenido.id).toBe('contenido');
    expect(shell.contenido.tagName).toBe('MAIN');
    expect(shell.contenido.getAttribute('tabindex')).toBe('-1');
  });

  it('mobile: barra inferior con Agenda, Bloqueos, Configuración y Más, oculta desde md', () => {
    preparar();
    const nav = navInferior();
    expect(nav?.getAttribute('aria-label')).toBe('Secciones');
    expect(nav?.className).toContain('md:hidden');
    const destinos = [...(nav?.querySelectorAll('a, button') ?? [])];
    expect(destinos.map(nombre)).toEqual(['Agenda', 'Bloqueos', 'Configuración', 'Más']);
    for (const d of destinos) {
      expect(d.querySelector('svg')).not.toBeNull();
      expect(d.className).toContain('min-h-destino-nav');
    }
  });

  it('desktop: barra lateral con las cinco secciones, "Cambiar contraseña" y "Cerrar sesión", visible solo desde md', () => {
    preparar();
    const nav = navLateral();
    expect(nav?.className).toMatch(/(^|\s)hidden(\s|$)/);
    expect(nav?.className).toContain('md:flex');
    expect([...(nav?.querySelectorAll('[data-destino], button') ?? [])].map(nombre)).toEqual([
      'Agenda',
      'Bloqueos',
      'Configuración',
      'Registro de cambios',
      'Exportar mis datos',
      'Cambiar contraseña',
      'Cerrar sesión',
    ]);
  });

  it('los destinos usan rutas /app/* que intercepta el router', () => {
    preparar();
    const hrefs = [...(navLateral()?.querySelectorAll('a[data-destino]') ?? [])].map((a) => a.getAttribute('href'));
    expect(hrefs).toEqual(['/app/agenda', '/app/bloqueos', '/app/configuracion', '/app/auditoria', '/app/exportar', '/app/cuenta/contrasena']);
  });
});

describe('shell: sección activa', () => {
  it('marca la sección actual con aria-current en ambas navegaciones y solo en ella', () => {
    const { shell } = preparar();
    shell.mostrar({ titulo: 'Configuración', seccion: 'configuracion' });

    const actuales = [...document.querySelectorAll('[aria-current="page"]')];
    expect(actuales).toHaveLength(2);
    expect(actuales.map(nombre)).toEqual(['Configuración', 'Configuración']);
    expect(actuales.every((a) => a.getAttribute('href') === '/app/configuracion')).toBe(true);
  });

  it('el indicador activo no depende solo del color (forma + ícono relleno)', () => {
    const { shell } = preparar();
    shell.mostrar({ titulo: 'Agenda', seccion: 'agenda' });
    const activo = navInferior()?.querySelector('[aria-current="page"]');
    // Barra de 3 px (pseudo-elemento) y el ícono relleno visible solo en el activo.
    expect(activo?.className).toContain('aria-[current=page]:before:h-indicador');
    expect(activo?.querySelector('[data-icono="relleno"]')?.getAttribute('class')).toContain('group-aria-[current=page]:block');
  });

  it('una página sin sección (no encontrada) no marca ningún destino', () => {
    const { shell } = preparar();
    shell.mostrar({ titulo: 'Agenda', seccion: 'agenda' });
    shell.mostrar({ titulo: 'Página no encontrada', seccion: null });
    expect(document.querySelectorAll('[aria-current="page"]')).toHaveLength(0);
  });

  it('"Cambiar contraseña" queda marcado como página actual en la barra lateral (C-07)', () => {
    const { shell } = preparar();
    shell.mostrar({ titulo: 'Cambiar contraseña', seccion: 'cuenta' });
    const actuales = [...document.querySelectorAll('[aria-current="page"]')];
    expect(actuales.map((e) => e.getAttribute('href'))).toEqual(['/app/cuenta/contrasena']);
  });

  it('muestra el título de la página y lo usa en el título del documento con la marca', () => {
    const { shell } = preparar();
    shell.mostrar({ titulo: 'Bloqueos', seccion: 'bloqueos' });
    expect(document.querySelector('h1')?.textContent).toBe('Bloqueos');
    expect(document.title).toBe('Bloqueos · Turnos Odonto (TBD)');
  });

  it('vacía el contenido y las acciones de la página anterior', () => {
    const { shell } = preparar();
    shell.contenido.append('viejo');
    shell.acciones.append(boton({ texto: 'Nuevo turno' }));
    shell.mostrar({ titulo: 'Bloqueos', seccion: 'bloqueos' });
    expect(shell.contenido.textContent).toBe('');
    expect(shell.acciones.childElementCount).toBe(0);
  });
});

describe('shell: marca', () => {
  it('muestra el nombre de la constante de marca en la barra superior y la lateral', () => {
    preparar('Otro Nombre');
    const marcas = [...document.querySelectorAll('[data-marca]')].map((e) => e.textContent);
    expect(marcas).toEqual(['Otro Nombre', 'Otro Nombre']);
  });
});

describe('shell: menú "Más" (mobile)', () => {
  it('abre una hoja modal con Registro de cambios, Exportar, Cambiar contraseña y Cerrar sesión', () => {
    preparar();
    const mas = [...(navInferior()?.querySelectorAll('button') ?? [])].find((b) => nombre(b) === 'Más');
    expect(mas?.getAttribute('aria-haspopup')).toBe('dialog');
    expect(mas?.getAttribute('aria-expanded')).toBe('false');

    mas?.click();

    const hoja = document.querySelector('[role="dialog"]');
    expect(hoja?.getAttribute('aria-modal')).toBe('true');
    expect(mas?.getAttribute('aria-expanded')).toBe('true');
    expect([...(hoja?.querySelectorAll('a, button') ?? [])].map(nombre)).toEqual(['Registro de cambios', 'Exportar mis datos', 'Cambiar contraseña', 'Cerrar sesión']);
  });

  it('"Cambiar contraseña" de la hoja lleva a /app/cuenta/contrasena y la cierra (C-07)', () => {
    preparar();
    [...(navInferior()?.querySelectorAll('button') ?? [])].find((b) => nombre(b) === 'Más')?.click();
    const enlace = [...document.querySelectorAll<HTMLAnchorElement>('[role="dialog"] a')].find((a) => nombre(a) === 'Cambiar contraseña');

    expect(enlace?.getAttribute('href')).toBe('/app/cuenta/contrasena');
    enlace?.click();
    expect(document.querySelector('[role="dialog"]')).toBeNull();
  });

  it('elegir un destino de la hoja la cierra', () => {
    preparar();
    [...(navInferior()?.querySelectorAll('button') ?? [])].find((b) => nombre(b) === 'Más')?.click();
    const enlace = document.querySelector<HTMLAnchorElement>('[role="dialog"] a[href="/app/auditoria"]');
    enlace?.addEventListener('click', (e) => e.preventDefault());

    enlace?.click();

    expect(document.querySelector('[role="dialog"]')).toBeNull();
  });

  it('"Cerrar sesión" llama a la acción de cierre', () => {
    const { alCerrarSesion } = preparar();
    [...(navLateral()?.querySelectorAll('button') ?? [])].find((b) => nombre(b) === 'Cerrar sesión')?.click();
    expect(alCerrarSesion).toHaveBeenCalledTimes(1);
  });
});

describe('shell: sin conexión', () => {
  const aviso = () => document.querySelector<HTMLElement>('[data-aviso-sin-conexion]');

  it('con conexión el aviso está oculto', () => {
    preparar();
    expect(aviso()?.hidden).toBe(true);
  });

  it('se pierde la conexión: aviso con la hora de lo guardado y escritura deshabilitada con motivo', () => {
    const { shell, conectividad } = preparar();
    const nuevo = boton({ texto: 'Nuevo turno', requiereConexion: true });
    shell.acciones.append(nuevo);

    conectividad.set({ online: false, ultimaSincronizacion: '2026-11-24T13:32:00Z' });

    expect(aviso()?.hidden).toBe(false);
    expect(aviso()?.getAttribute('role')).toBe('status');
    expect(aviso()?.textContent).toContain('Sin conexión.');
    expect(aviso()?.textContent).toContain('Mostrando tu agenda de las 10:32.');
    expect(nuevo.disabled).toBe(true);
    const motivo = document.getElementById(nuevo.getAttribute('aria-describedby') ?? '');
    expect(motivo?.textContent).toBe('Necesitás conexión para crear o cambiar datos.');
  });

  it('sin nada guardado, el aviso no inventa una hora', () => {
    const { conectividad } = preparar();
    conectividad.set({ online: false });
    expect(aviso()?.textContent).toContain('Sin conexión.');
    expect(aviso()?.textContent).not.toContain('Mostrando');
  });

  it('las acciones que aparecen mientras está offline también se deshabilitan', () => {
    const { shell, conectividad } = preparar();
    conectividad.set({ online: false });
    const nuevo = boton({ texto: 'Nuevo turno', requiereConexion: true });

    shell.acciones.append(nuevo);
    shell.sincronizarConexion();

    expect(nuevo.disabled).toBe(true);
  });

  it('una acción agregada offline se deshabilita sola (observador del DOM)', async () => {
    const { shell, conectividad } = preparar();
    conectividad.set({ online: false });
    const nuevo = boton({ texto: 'Nuevo turno', requiereConexion: true });

    shell.contenido.append(nuevo);
    await Promise.resolve();

    expect(nuevo.disabled).toBe(true);
  });

  it('offline no entra en bucle: el propio aviso no re-dispara la sincronización', async () => {
    const { conectividad } = preparar();
    conectividad.set({ online: false, ultimaSincronizacion: '2026-11-24T13:32:00Z' });

    // Si el observador reaccionara a sus propios cambios, las microtareas nunca terminarían y esto no resolvería.
    await new Promise((resolver) => setTimeout(resolver, 20));

    expect(aviso()?.hidden).toBe(false);
  });

  it('vuelve la conexión: el aviso desaparece y las acciones se habilitan', () => {
    const { shell, conectividad } = preparar();
    const nuevo = boton({ texto: 'Nuevo turno', requiereConexion: true });
    shell.acciones.append(nuevo);
    conectividad.set({ online: false });

    conectividad.set({ online: true });

    expect(aviso()?.hidden).toBe(true);
    expect(nuevo.disabled).toBe(false);
    expect(nuevo.hasAttribute('aria-describedby')).toBe(false);
  });
});
