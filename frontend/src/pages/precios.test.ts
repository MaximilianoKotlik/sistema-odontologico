import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { catalogoDeEjemplo } from '../features/precios/catalogo-de-ejemplo';
import { ApiError } from '../shared/http';
import { montarPrecios, type FuenteDeCatalogo } from './precios';

const RUTA = '/api/v1/public/planes';

function fuenteQueResponde(...respuestas: (() => Promise<unknown>)[]): FuenteDeCatalogo & { pedidos: string[] } {
  const pedidos: string[] = [];
  return {
    pedidos,
    get<T>(ruta: string): Promise<T> {
      pedidos.push(ruta);
      const siguiente = respuestas.shift();
      if (siguiente === undefined) throw new Error('pedido inesperado');
      return siguiente() as Promise<T>;
    },
  };
}

const texto = (raiz: ParentNode) => (raiz.textContent ?? '').replace(/\s+/g, ' ');

describe('montarPrecios', () => {
  let contenedor: HTMLElement;

  beforeEach(() => {
    document.body.innerHTML = '<div id="raiz"></div>';
    const raiz = document.querySelector<HTMLElement>('#raiz');
    if (raiz === null) throw new Error('sin raíz');
    contenedor = raiz;
  });
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('pide el catálogo público y muestra plan, inclusiones, importe en ARS y fecha', async () => {
    const fuente = fuenteQueResponde(() => Promise.resolve(catalogoDeEjemplo));

    await montarPrecios(contenedor, { http: fuente, marca: 'Muelas Felices' });

    expect(fuente.pedidos).toEqual([RUTA]);
    const contenido = texto(contenedor);
    expect(contenedor.querySelectorAll('h1')).toHaveLength(1);
    expect(contenedor.querySelector('h1')?.textContent).toContain('Muelas Felices');
    expect(contenedor.querySelector('h2')?.textContent).toBe('Plan consultorio');
    expect(contenido).toContain('Un odontólogo · un consultorio.');
    expect(contenido).toContain('$ 14.900');
    expect(contenido).toContain('por mes');
    expect(contenido).toContain('impuestos incluidos');
    const inclusiones = [...contenedor.querySelectorAll('[data-incluye] li')].map((li) => li.textContent);
    expect(inclusiones).toEqual(['Agenda diaria y semanal', 'Bloqueos y sobreturnos marcados']);
    const fecha = contenedor.querySelector('time');
    expect(fecha?.getAttribute('datetime')).toBe('2026-11-20T03:00:00Z');
    expect(fecha?.textContent).toBe('20 de noviembre de 2026');
    expect(contenido).toContain('Última actualización');
  });

  it('dice explícitamente que la prueba de 30 días no pide tarjeta ni medio de pago', async () => {
    await montarPrecios(contenedor, { http: fuenteQueResponde(() => Promise.resolve(catalogoDeEjemplo)), marca: 'Muelas Felices' });

    expect(texto(contenedor)).toContain('30 días de prueba sin tarjeta ni medio de pago');
  });

  it('no afirma cobros automáticos, 2FA, certificaciones ni cumplimiento verificado', async () => {
    await montarPrecios(contenedor, { http: fuenteQueResponde(() => Promise.resolve(catalogoDeEjemplo)), marca: 'Muelas Felices' });

    const contenido = texto(contenedor).toLowerCase();
    for (const claim of ['cobramos automáticamente', 'débito automático', '2fa', 'dos factores', 'certificad', 'cumplimos', 'iso 27001']) {
      expect(contenido, claim).not.toContain(claim);
    }
  });

  it('ofrece crear el consultorio con un enlace al registro de al menos 44 px', async () => {
    await montarPrecios(contenedor, { http: fuenteQueResponde(() => Promise.resolve(catalogoDeEjemplo)), marca: 'Muelas Felices' });

    const cta = [...contenedor.querySelectorAll('a')].find((a) => a.textContent === 'Crear mi consultorio');
    expect(cta?.getAttribute('href')).toBe('/registro');
    expect(cta?.className).toContain('min-h-touch');
  });

  it('mientras carga se anuncia ocupado y no muestra importes', () => {
    const fuente = fuenteQueResponde(() => new Promise(() => undefined));

    void montarPrecios(contenedor, { http: fuente, marca: 'Muelas Felices' });

    expect(contenedor.querySelector('[aria-busy="true"]')).not.toBeNull();
    expect(texto(contenedor)).toContain('Cargando precios');
    expect(texto(contenedor)).not.toContain('$');
  });

  it('si falla muestra un error reintentable sin importes ni inclusiones inventados, y reintenta', async () => {
    const fuente = fuenteQueResponde(
      () => Promise.reject(new ApiError(503, 'PLANES_NO_DISPONIBLES', 'No disponible')),
      () => Promise.resolve(catalogoDeEjemplo),
    );

    await montarPrecios(contenedor, { http: fuente, marca: 'Muelas Felices' });

    const alerta = contenedor.querySelector('[role="alert"]');
    expect(alerta?.textContent).toContain('No pudimos cargar los precios.');
    expect(texto(contenedor)).not.toContain('$');
    expect(contenedor.querySelector('[data-incluye]')).toBeNull();

    const reintentar = [...contenedor.querySelectorAll('button')].find((b) => b.textContent === 'Reintentar');
    expect(reintentar).toBeDefined();
    reintentar?.click();
    await vi.waitFor(() => expect(texto(contenedor)).toContain('$ 14.900'));
    expect(fuente.pedidos).toEqual([RUTA, RUTA]);
  });

  it('una respuesta que no cumple el contrato se trata como error, sin mostrar sus datos', async () => {
    const roto = { ...catalogoDeEjemplo, planes: [{ ...catalogoDeEjemplo.planes[0], importeARS: 'PENDIENTE' }] };

    await montarPrecios(contenedor, { http: fuenteQueResponde(() => Promise.resolve(roto)), marca: 'Muelas Felices' });

    expect(contenedor.querySelector('[role="alert"]')).not.toBeNull();
    expect(texto(contenedor)).not.toContain('PENDIENTE');
  });
});
