import { afterEach, describe, expect, it, vi } from 'vitest';
import { vigilarCambios, type Navegacion } from './cambios';

// Tarea 3.5 de C-09 (design D9): confirmación al salir con cambios y beforeunload solo mientras hay cambios.

function navegacionFalsa() {
  let confirmar: (() => Promise<boolean>) | null = null;
  const navegacion: Navegacion = {
    navegar: vi.fn(async () => undefined),
    bloquearSalida: vi.fn((fn: () => Promise<boolean>) => {
      confirmar = fn;
      return () => {
        if (confirmar === fn) confirmar = null;
      };
    }),
  };
  return { navegacion, bloqueo: () => confirmar };
}

const boton = (texto: string) => [...document.querySelectorAll<HTMLButtonElement>('[role="dialog"] button')].find((b) => b.textContent === texto);
const beforeunload = () => {
  const evento = new Event('beforeunload', { cancelable: true });
  window.dispatchEvent(evento);
  return evento.defaultPrevented;
};

afterEach(() => {
  document.body.replaceChildren();
});

describe('vigilarCambios', () => {
  it('sin cambios no bloquea ni pide confirmar al cerrar la pestaña', () => {
    const { navegacion, bloqueo } = navegacionFalsa();
    vigilarCambios(navegacion, window);

    expect(bloqueo()).toBeNull();
    expect(beforeunload()).toBe(false);
  });

  it('con cambios bloquea la salida y pide confirmar al cerrar o recargar', () => {
    const { navegacion, bloqueo } = navegacionFalsa();
    const vigia = vigilarCambios(navegacion, window);

    vigia.marcar(true);

    expect(bloqueo()).not.toBeNull();
    expect(beforeunload()).toBe(true);
    vigia.detener();
  });

  it('al volver a estar sin cambios deja de bloquear', () => {
    const { navegacion, bloqueo } = navegacionFalsa();
    const vigia = vigilarCambios(navegacion, window);

    vigia.marcar(true);
    vigia.marcar(false);

    expect(bloqueo()).toBeNull();
    expect(beforeunload()).toBe(false);
  });

  it('pregunta "¿Descartar los cambios?" con "Seguir editando" enfocado y se queda si se elige', async () => {
    const { navegacion, bloqueo } = navegacionFalsa();
    const vigia = vigilarCambios(navegacion, window);
    vigia.marcar(true);

    const respuesta = bloqueo()?.();
    const dialogo = document.querySelector('[role="dialog"]');
    expect(dialogo?.textContent).toContain('¿Descartar los cambios?');
    expect(document.activeElement).toBe(boton('Seguir editando'));
    boton('Seguir editando')?.click();

    await expect(respuesta).resolves.toBe(false);
    expect(bloqueo()).not.toBeNull();
    vigia.detener();
  });

  it('si se descarta, deja salir y suelta el beforeunload', async () => {
    const { navegacion, bloqueo } = navegacionFalsa();
    const vigia = vigilarCambios(navegacion, window);
    vigia.marcar(true);

    const respuesta = bloqueo()?.();
    boton('Descartar')?.click();

    await expect(respuesta).resolves.toBe(true);
    expect(beforeunload()).toBe(false);
  });

  it('cerrar el diálogo con Escape cuenta como seguir editando', async () => {
    const { navegacion, bloqueo } = navegacionFalsa();
    const vigia = vigilarCambios(navegacion, window);
    vigia.marcar(true);

    const respuesta = bloqueo()?.();
    document.activeElement?.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));

    await expect(respuesta).resolves.toBe(false);
    vigia.detener();
  });
});
