import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { aviso } from './aviso';
import { boton, clasesBoton, enlaceBoton } from './boton';
import { campo } from './campo';
import { chipEstado } from './chip-estado';
import { dialogo } from './dialogo';
import { errorRecuperable } from './error-recuperable';
import { esqueleto } from './esqueleto';
import { estadoVacio } from './estado-vacio';

/** Nombre accesible simplificado: aria-label, o texto visible sin los íconos ocultos. */
function nombreAccesible(elemento: Element): string {
  const etiqueta = elemento.getAttribute('aria-label');
  if (etiqueta !== null) return etiqueta;
  const copia = elemento.cloneNode(true) as Element;
  copia.querySelectorAll('[aria-hidden="true"]').forEach((n) => n.remove());
  return (copia.textContent ?? '').replace(/\s+/g, ' ').trim();
}

beforeEach(() => {
  document.body.innerHTML = '';
});

describe('boton', () => {
  it('es un <button type="button"> con nombre accesible e ícono oculto a lectores', () => {
    const b = boton({ texto: 'Nuevo turno', icono: 'mas' });
    expect(b.tagName).toBe('BUTTON');
    expect(b.type).toBe('button');
    expect(nombreAccesible(b)).toBe('Nuevo turno');
    expect(b.querySelector('svg')?.getAttribute('aria-hidden')).toBe('true');
  });

  it('aplica la variante con clases de tokens', () => {
    expect(boton({ texto: 'A' }).className).toContain('bg-primario');
    expect(boton({ texto: 'B', variante: 'secundario' }).className).toContain('border-primario');
    expect(boton({ texto: 'C', variante: 'peligro' }).className).toContain('bg-peligro');
  });

  it('tiene altura táctil mínima de 44 px', () => {
    expect(boton({ texto: 'A' }).className).toContain('min-h-touch');
  });

  it('puede ocultar el texto en mobile sin perder el nombre accesible', () => {
    const b = boton({ texto: 'Nuevo turno', icono: 'mas', textoSoloDesktop: true });
    expect(nombreAccesible(b)).toBe('Nuevo turno');
    expect(b.querySelector('span')?.className).toContain('max-md:sr-only');
  });

  it('ejecuta la acción al hacer clic', () => {
    const fn = vi.fn();
    boton({ texto: 'A', alHacerClic: fn }).click();
    expect(fn).toHaveBeenCalledTimes(1);
  });

  it('marca las acciones de escritura para deshabilitarlas sin conexión', () => {
    expect(boton({ texto: 'Nuevo turno', requiereConexion: true }).hasAttribute('data-requiere-conexion')).toBe(true);
  });
});

describe('clasesBoton', () => {
  // Con `border-transparent` en la base, el CSS generado lo ponía después de `border-primario` y el secundario quedaba sin
  // borde (encontrado en la verificación visual de C-13). El borde transparente va solo en las variantes sin borde propio.
  it('el secundario y el peligro secundario tienen su borde de color y ningún borde transparente', () => {
    expect(clasesBoton('secundario')).toContain('border-primario');
    expect(clasesBoton('secundario')).not.toContain('border-transparent');
    expect(clasesBoton('peligro-secundario')).toContain('border-peligro');
    expect(clasesBoton('peligro-secundario')).toContain('text-peligro');
    expect(clasesBoton('peligro-secundario')).not.toContain('border-transparent');
  });

  it('las variantes rellenas y la fantasma usan un borde transparente', () => {
    for (const variante of ['primario', 'fantasma', 'peligro'] as const) expect(clasesBoton(variante)).toContain('border-transparent');
  });
});

describe('enlaceBoton', () => {
  it('es un enlace del router con aspecto de botón', () => {
    const a = enlaceBoton({ texto: 'Ir a mi agenda', href: '/app/agenda' });
    expect(a.tagName).toBe('A');
    expect(a.getAttribute('href')).toBe('/app/agenda');
    expect(a.hasAttribute('data-link')).toBe(true);
    expect(a.className).toContain('min-h-touch');
  });
});

describe('campo', () => {
  it('asocia etiqueta, ayuda y control', () => {
    const c = campo({ etiqueta: 'Email', nombre: 'email', tipo: 'email', ayuda: 'Lo usás para entrar.' });
    document.body.append(c);
    const input = document.querySelector('input');
    const label = document.querySelector('label');
    expect(label?.htmlFor).toBe(input?.id);
    const ayuda = document.getElementById(input?.getAttribute('aria-describedby') ?? '');
    expect(ayuda?.textContent).toBe('Lo usás para entrar.');
    expect(input?.type).toBe('email');
    expect(input?.className).toContain('min-h-touch');
  });

  it('con error marca aria-invalid y anuncia el mensaje', () => {
    const c = campo({ etiqueta: 'Email', nombre: 'email', error: 'Ingresá un email válido.' });
    document.body.append(c);
    const input = document.querySelector('input');
    expect(input?.getAttribute('aria-invalid')).toBe('true');
    const ids = (input?.getAttribute('aria-describedby') ?? '').split(' ');
    expect(ids.map((id) => document.getElementById(id)?.textContent)).toContain('Ingresá un email válido.');
  });

  it('dos campos con el mismo nombre no repiten id', () => {
    document.body.append(campo({ etiqueta: 'A', nombre: 'x' }), campo({ etiqueta: 'B', nombre: 'x' }));
    const ids = [...document.querySelectorAll('input')].map((i) => i.id);
    expect(new Set(ids).size).toBe(2);
  });
});

describe('chipEstado', () => {
  it.each([
    ['reservado', 'Reservado'],
    ['confirmado', 'Confirmado'],
    ['cancelado', 'Cancelado'],
    ['sobreturno', 'Sobreturno'],
    ['bloqueo', 'Bloqueo'],
  ] as const)('el estado %s tiene texto "%s" e ícono (no solo color)', (estado, texto) => {
    const chip = chipEstado(estado);
    expect(nombreAccesible(chip)).toBe(texto);
    expect(chip.querySelector('svg')).not.toBeNull();
    expect(chip.className).toContain(`bg-${estado}-bg`);
  });
});

describe('dialogo', () => {
  afterEach(() => {
    document.body.innerHTML = '';
  });

  it('es modal, tiene título y mueve el foco adentro al abrir', () => {
    const disparador = boton({ texto: 'Abrir' });
    document.body.append(disparador);
    disparador.focus();
    const d = dialogo({ titulo: '¿Cancelar el turno?', descripcion: 'El paciente recibe un aviso.', acciones: [boton({ texto: 'Volver' })] });

    d.abrir();

    const caja = document.querySelector('[role="dialog"]');
    expect(caja?.getAttribute('aria-modal')).toBe('true');
    expect(document.getElementById(caja?.getAttribute('aria-labelledby') ?? '')?.textContent).toBe('¿Cancelar el turno?');
    expect(caja?.contains(document.activeElement)).toBe(true);
  });

  it('Escape cierra y devuelve el foco a quien lo abrió', () => {
    const disparador = boton({ texto: 'Abrir' });
    document.body.append(disparador);
    disparador.focus();
    const alCerrar = vi.fn();
    const d = dialogo({ titulo: 'T', acciones: [boton({ texto: 'Aceptar' })], alCerrar });
    d.abrir();

    document.activeElement?.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));

    expect(document.querySelector('[role="dialog"]')).toBeNull();
    expect(document.activeElement).toBe(disparador);
    expect(alCerrar).toHaveBeenCalledTimes(1);
  });

  it('Tab desde el último control vuelve al primero (foco atrapado)', () => {
    const d = dialogo({ titulo: 'T', acciones: [boton({ texto: 'Uno' }), boton({ texto: 'Dos' })] });
    d.abrir();
    const botones = [...document.querySelectorAll<HTMLButtonElement>('[role="dialog"] button')];
    botones.at(-1)?.focus();

    const evento = new KeyboardEvent('keydown', { key: 'Tab', bubbles: true, cancelable: true });
    botones.at(-1)?.dispatchEvent(evento);

    expect(evento.defaultPrevented).toBe(true);
    expect(document.activeElement).toBe(botones[0]);
  });

  it('tocar el velo cierra el diálogo', () => {
    const d = dialogo({ titulo: 'T', acciones: [] });
    d.abrir();
    document.querySelector<HTMLElement>('[data-velo]')?.click();
    expect(document.querySelector('[role="dialog"]')).toBeNull();
  });
});

describe('aviso (toast)', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it('se anuncia en una región viva y desaparece solo', () => {
    aviso('Link copiado.', { duracionMs: 4000 });
    const region = document.querySelector('[data-region-avisos]');
    expect(region?.getAttribute('aria-live')).toBe('polite');
    expect(region?.textContent).toContain('Link copiado.');

    vi.advanceTimersByTime(4000);

    expect(region?.textContent).not.toContain('Link copiado.');
  });

  it('reutiliza la misma región para varios avisos', () => {
    aviso('Uno');
    aviso('Dos');
    expect(document.querySelectorAll('[data-region-avisos]')).toHaveLength(1);
  });

  it('un aviso de error usa role="alert"', () => {
    aviso('No se pudo guardar.', { tono: 'error' });
    expect(document.querySelector('[role="alert"]')?.textContent).toContain('No se pudo guardar.');
  });
});

describe('estadoVacio', () => {
  it('muestra ícono, título, explicación y la acción principal', () => {
    const vacio = estadoVacio({
      icono: 'calendario',
      titulo: 'Todavía no tenés turnos hoy',
      texto: 'Compartí tu link de reservas para que tus pacientes elijan un horario libre.',
      acciones: [boton({ texto: 'Copiar mi link' })],
    });
    expect(vacio.querySelector('h2')?.textContent).toBe('Todavía no tenés turnos hoy');
    expect(vacio.querySelector('svg')?.getAttribute('aria-hidden')).toBe('true');
    expect(vacio.textContent).toContain('Copiar mi link');
  });
});

describe('esqueleto', () => {
  it('se anuncia como ocupado con un texto para lectores', () => {
    const e = esqueleto({ etiqueta: 'Cargando tu agenda…' });
    expect(e.getAttribute('aria-busy')).toBe('true');
    expect(e.querySelector('.sr-only')?.textContent).toBe('Cargando tu agenda…');
  });

  it('el brillo solo se anima si el sistema no pide movimiento reducido', () => {
    const bloques = [...esqueleto().querySelectorAll('.esqueleto')];
    expect(bloques.length).toBeGreaterThan(0);
    for (const b of bloques) {
      expect(b.className).toContain('motion-safe:animate-brillo');
      expect(b.className.split(' ')).not.toContain('animate-brillo');
    }
  });
});

describe('errorRecuperable', () => {
  it('muestra el error como alerta y "Reintentar" repite la carga', () => {
    const reintentar = vi.fn();
    const e = errorRecuperable({ titulo: 'No pudimos cargar tu agenda', codigo: 'ERROR_INTERNO', alReintentar: reintentar });
    expect(e.getAttribute('role')).toBe('alert');
    expect(e.querySelector('h2')?.textContent).toBe('No pudimos cargar tu agenda');
    expect(e.textContent).toContain('Código: ERROR_INTERNO');

    const boton = [...e.querySelectorAll('button')].find((b) => nombreAccesible(b) === 'Reintentar');
    boton?.click();

    expect(reintentar).toHaveBeenCalledTimes(1);
  });
});
