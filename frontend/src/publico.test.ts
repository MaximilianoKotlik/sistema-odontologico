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

  it('/seguridad es pública y se monta sin shell, sin pedidos y con su título (C-22, design D5)', async () => {
    const pedidos: string[] = [];
    const fetchFalso: typeof fetch = (entrada) => {
      pedidos.push(String(entrada));
      return Promise.reject(new Error('la página no debería pedir nada'));
    };

    expect(esRutaPublica('/seguridad')).toBe(true);
    expect(esRutaPublica('/seguridad/')).toBe(true);
    await montarPaginaPublica(document, '/seguridad', { fetch: fetchFalso });

    expect(document.querySelector('[data-app-root]')).toBeNull();
    expect(document.title).toBe('Seguridad y datos · Muelas Felices');
    expect(document.querySelector('#app main#contenido h1')?.textContent).toBe('Seguridad y datos');
    expect(pedidos).toEqual([]);
  });

  it('/c/:slug y /t/:token son rutas públicas con parámetro (C-16, design D1)', () => {
    expect(esRutaPublica('/c/demo')).toBe(true);
    expect(esRutaPublica('/c/demo/')).toBe(true);
    expect(esRutaPublica('/t/ejemplo-ficticio-7Qm2')).toBe(true);
    expect(esRutaPublica('/c/')).toBe(false);
    expect(esRutaPublica('/c')).toBe(false);
    expect(esRutaPublica('/c/demo/otra')).toBe(false);
    expect(esRutaPublica('/t/')).toBe(false);
  });

  it.each([
    ['/c/demo', '/api/v1/public/consultorios/demo'],
    ['/t/ejemplo-ficticio-7Qm2', '/api/v1/public/turnos/ejemplo-ficticio-7Qm2'],
  ])('monta %s sin el shell, sin sesión y sin credenciales', async (ruta, pedidoEsperado) => {
    const pedidos: { url: string; init: RequestInit | undefined }[] = [];
    const fetchFalso: typeof fetch = (entrada, init) => {
      pedidos.push({ url: String(entrada), init });
      return Promise.resolve(
        new Response(JSON.stringify({ codigo: 'RECURSO_NO_ENCONTRADO', detail: 'No encontrado' }), {
          status: 404,
          headers: { 'Content-Type': 'application/problem+json' },
        }),
      );
    };

    await montarPaginaPublica(document, ruta, { fetch: fetchFalso });

    expect(document.querySelector('[data-app-root]')).toBeNull();
    expect(document.querySelector('#app main#contenido')).not.toBeNull();
    expect(pedidos[0]?.url).toBe(pedidoEsperado);
    for (const pedido of pedidos) {
      expect(new Headers(pedido.init?.headers).has('Authorization')).toBe(false);
      expect(pedido.init?.credentials).not.toBe('include');
    }
  });

  it('un token con % mal formado no rompe la página: la API decide que el link no funciona', async () => {
    const pedidos: string[] = [];
    const fetchFalso: typeof fetch = (entrada) => {
      pedidos.push(String(entrada));
      return Promise.resolve(new Response(JSON.stringify({ codigo: 'RECURSO_NO_ENCONTRADO' }), { status: 404, headers: { 'Content-Type': 'application/json' } }));
    };

    await montarPaginaPublica(document, '/t/%E0%A4%A', { fetch: fetchFalso });

    expect(pedidos).toHaveLength(1);
    expect(pedidos[0]).toMatch(/^\/api\/v1\/public\/turnos\//);
  });

  it('falla con un mensaje claro si falta #app', async () => {
    document.body.innerHTML = '';
    await expect(montarPaginaPublica(document, '/precios')).rejects.toThrow('No se encontró el elemento #app');
  });
});
