import { describe, expect, it } from 'vitest';
import { montarPrecios } from '../../pages/precios';
import { catalogoDeEjemplo } from '../precios/catalogo-de-ejemplo';
import { montarLayoutAuth } from './layout';

// Revisión de Maxi en #57: el precio del panel del login sale de la misma fuente que /precios (C-21), nunca a mano.

const esperar = () => new Promise((resolver) => setTimeout(resolver, 0));
const sinEspaciosRaros = (texto: string | null | undefined) => (texto ?? '').replace(/\s/g, ' ');

function panelCon(cargarCatalogo: () => Promise<unknown>) {
  const anfitrion = document.createElement('div');
  document.body.replaceChildren(anfitrion);
  montarLayoutAuth(anfitrion, 'Muelas Felices', true, cargarCatalogo);
  return () => anfitrion.querySelector<HTMLElement>('[data-precio-panel]');
}

describe('panel de marca del login: precio', () => {
  it('muestra el mismo importe que la página /precios para el mismo catálogo', async () => {
    const precios = document.createElement('div');
    await montarPrecios(precios, { http: { get: async <T,>() => catalogoDeEjemplo as T }, marca: 'Muelas Felices' });
    const importeDePrecios = sinEspaciosRaros(precios.querySelector('section[aria-labelledby^="plan-"] .text-2xl')?.textContent);

    const panel = panelCon(async () => catalogoDeEjemplo);
    await esperar();

    expect(importeDePrecios).toBe('$ 14.900');
    expect(sinEspaciosRaros(panel()?.textContent)).toContain(`${importeDePrecios} por mes`);
    expect(panel()?.querySelector('a[href="/precios"]')?.textContent).toBe('Ver precios');
  });

  it('si el catálogo cambia, el panel cambia con él', async () => {
    const otro = { ...catalogoDeEjemplo, planes: catalogoDeEjemplo.planes.map((plan) => ({ ...plan, importeARS: '19990.50' })) };

    const panel = panelCon(async () => otro);
    await esperar();

    expect(sinEspaciosRaros(panel()?.textContent)).toContain('$ 19.990,50 por mes');
  });

  it('sin catálogo válido no inventa un importe: deja solo "Ver precios"', async () => {
    for (const cargar of [async () => Promise.reject(new Error('sin red')), async () => ({ planes: [] })]) {
      const panel = panelCon(cargar);
      await esperar();

      expect(panel()?.textContent).not.toMatch(/\$|ARS/);
      expect(panel()?.querySelector('a[href="/precios"]')?.textContent).toBe('Ver precios');
    }
  });
});
