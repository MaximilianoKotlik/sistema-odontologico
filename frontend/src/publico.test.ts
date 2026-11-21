import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { catalogoDeEjemplo } from './features/precios/catalogo-de-ejemplo';
import { esRutaPublica, montarPaginaPublica } from './publico';

const respuestaJson = (cuerpo: unknown) =>
  new Response(JSON.stringify(cuerpo), { status: 200, headers: { 'Content-Type': 'application/json' } });

describe('páginas públicas', () => {
  beforeEach(() => {
    document.body.innerHTML = '<div id="app"></div>';
    document.title = '';
  });
  afterEach(() => {
    history.replaceState(null, '', '/');
  });

  it('/precios es una ruta pública y las de la app no', () => {
    expect(esRutaPublica('/precios')).toBe(true);
    expect(esRutaPublica('/precios/')).toBe(true);
    expect(esRutaPublica('/app/agenda')).toBe(false);
    expect(esRutaPublica('/')).toBe(false);
    expect(esRutaPublica('/precios-viejos')).toBe(false);
  });

  it('monta /precios sin el shell del odontólogo y sin mandar credenciales', async () => {
    const pedidos: { url: string; init: RequestInit | undefined }[] = [];
    const fetchFalso: typeof fetch = (entrada, init) => {
      pedidos.push({ url: String(entrada), init });
      return Promise.resolve(respuestaJson(catalogoDeEjemplo));
    };

    await montarPaginaPublica(document, '/precios', { fetch: fetchFalso });

    expect(document.querySelector('[data-app-root]')).toBeNull();
    expect(document.querySelector('#app main#contenido h1')).not.toBeNull();
    expect(document.title).toBe('Precios · Muelas Felices');
    expect(pedidos).toHaveLength(1);
    expect(pedidos[0]?.url).toBe('/api/v1/public/planes');
    expect(new Headers(pedidos[0]?.init?.headers).has('Authorization')).toBe(false);
    expect(pedidos[0]?.init?.credentials).not.toBe('include');
  });

  it('falla con un mensaje claro si falta #app', async () => {
    document.body.innerHTML = '';
    await expect(montarPaginaPublica(document, '/precios')).rejects.toThrow('No se encontró el elemento #app');
  });
});
