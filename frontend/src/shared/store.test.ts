import { describe, expect, it, vi } from 'vitest';
import { crearStore } from './store';

describe('crearStore', () => {
  it('devuelve el estado inicial', () => {
    const store = crearStore({ online: true, ultimaSincronizacion: null as string | null });
    expect(store.get()).toEqual({ online: true, ultimaSincronizacion: null });
  });

  it('set aplica cambios parciales sin perder el resto', () => {
    const store = crearStore({ online: true, ultimaSincronizacion: null as string | null });
    store.set({ online: false });
    expect(store.get()).toEqual({ online: false, ultimaSincronizacion: null });
  });

  it('avisa a los suscriptores con el estado nuevo y el anterior', () => {
    const store = crearStore({ n: 1 });
    const fn = vi.fn();
    store.suscribir(fn);
    store.set({ n: 2 });
    expect(fn).toHaveBeenCalledWith({ n: 2 }, { n: 1 });
  });

  it('no avisa si el cambio no modifica nada', () => {
    const store = crearStore({ n: 1 });
    const fn = vi.fn();
    store.suscribir(fn);
    store.set({ n: 1 });
    expect(fn).not.toHaveBeenCalled();
  });

  it('suscribir devuelve una función para desuscribirse', () => {
    const store = crearStore({ n: 1 });
    const fn = vi.fn();
    const desuscribir = store.suscribir(fn);
    desuscribir();
    store.set({ n: 2 });
    expect(fn).not.toHaveBeenCalled();
  });

  it('el estado devuelto no se puede mutar desde afuera', () => {
    const store = crearStore({ n: 1 });
    expect(() => {
      (store.get() as { n: number }).n = 5;
    }).toThrow();
    expect(store.get().n).toBe(1);
  });
});
