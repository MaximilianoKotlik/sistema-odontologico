import { beforeEach, describe, expect, it } from 'vitest';
import { montarAplicacion } from './app';

describe('montarAplicacion', () => {
  beforeEach(() => {
    document.body.innerHTML = '<div id="app"></div>';
  });

  it('monta el contenedor raíz dentro de #app', () => {
    montarAplicacion(document);

    const raiz = document.querySelector('#app > [data-app-root]');
    expect(raiz).not.toBeNull();
    expect(raiz?.tagName).toBe('MAIN');
  });

  it('no duplica el contenedor si se monta dos veces', () => {
    montarAplicacion(document);
    montarAplicacion(document);

    expect(document.querySelectorAll('[data-app-root]')).toHaveLength(1);
  });

  it('falla con un mensaje claro si falta #app', () => {
    document.body.innerHTML = '';

    expect(() => montarAplicacion(document)).toThrow('No se encontró el elemento #app');
  });
});
