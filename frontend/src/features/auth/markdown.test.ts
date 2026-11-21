import { describe, expect, it } from 'vitest';
import { renderizarMarkdown } from './markdown';

// Tarea 3.6 de C-07 (design D9, aprobada por Maxi en #37): el conversor arma el DOM, nunca HTML.

const html = (markdown: string) => {
  const contenedor = document.createElement('div');
  contenedor.append(renderizarMarkdown(markdown));
  return contenedor;
};

describe('renderizarMarkdown', () => {
  it('títulos, párrafos, énfasis y citas', () => {
    const c = html('# Título\n\n## Sección\n\nUn **párrafo** con *énfasis*.\n\n> Borrador pendiente de revisión legal.');

    expect(c.querySelectorAll('h1')).toHaveLength(1);
    expect(c.querySelector('h1')?.textContent).toBe('Título');
    expect(c.querySelector('h2')?.textContent).toBe('Sección');
    expect(c.querySelector('p strong')?.textContent).toBe('párrafo');
    expect(c.querySelector('p em')?.textContent).toBe('énfasis');
    expect(c.querySelector('blockquote')?.textContent).toContain('Borrador pendiente de revisión legal.');
  });

  it('listas con viñetas y numeradas', () => {
    const c = html('- uno\n- dos\n\n1. primero\n2. segundo');

    expect([...c.querySelectorAll('ul li')].map((li) => li.textContent)).toEqual(['uno', 'dos']);
    expect([...c.querySelectorAll('ol li')].map((li) => li.textContent)).toEqual(['primero', 'segundo']);
  });

  it('tablas con encabezado', () => {
    const c = html('| Dato | Para qué |\n|---|---|\n| Email | Ingresar |\n| Teléfono | Avisos |');

    expect([...c.querySelectorAll('thead th')].map((th) => th.textContent)).toEqual(['Dato', 'Para qué']);
    expect([...c.querySelectorAll('tbody tr')].map((tr) => tr.textContent)).toEqual(['EmailIngresar', 'TeléfonoAvisos']);
  });

  it('el HTML del Markdown se muestra como texto y no crea elementos', () => {
    const c = html('<script>alert(1)</script>\n\nHola <img src=x onerror=alert(1)> mundo');

    expect(c.querySelector('script')).toBeNull();
    expect(c.querySelector('img')).toBeNull();
    expect(c.textContent).toContain('<script>alert(1)</script>');
    expect(c.textContent).toContain('<img src=x onerror=alert(1)>');
  });

  it('solo acepta links https: y mailto:; otros protocolos y rutas del repo quedan como texto', () => {
    const c = html(
      '[x](javascript:alert(1)) [web](https://example.com/legal) [mail](mailto:legal@example.com) [kb](../knowledge-base/11.md) [http](http://example.com) [datos](data:text/html,hola)',
    );

    const hrefs = [...c.querySelectorAll('a')].map((a) => a.getAttribute('href'));
    expect(hrefs).toEqual(['https://example.com/legal', 'mailto:legal@example.com']);
    expect([...c.querySelectorAll('a')].some((a) => (a.getAttribute('href') ?? '').startsWith('javascript:'))).toBe(false);
    expect(c.querySelector('a[href^="https:"]')?.getAttribute('rel')).toBe('noopener noreferrer');
    expect(c.textContent).toContain('x');
    expect(c.textContent).toContain('kb');
  });

  it('une las líneas de un párrafo y de una cita, y muestra el código en línea', () => {
    const c = html('Primera línea\nsegunda línea con `2026-11-borrador`.\n\n> Cita de\n> dos líneas.');

    expect(c.querySelector('p')?.textContent).toBe('Primera línea segunda línea con 2026-11-borrador.');
    expect(c.querySelector('p code')?.textContent).toBe('2026-11-borrador');
    expect(c.querySelector('blockquote')?.textContent).toBe('Cita de dos líneas.');
  });

  it('un ítem de lista sigue en las líneas con sangría', () => {
    const c = html('- **Datos**: nombre y\n  apellido.\n- Otro');

    expect([...c.querySelectorAll('li')].map((li) => li.textContent)).toEqual(['Datos: nombre y apellido.', 'Otro']);
  });

  it('deja los marcadores [COMPLETAR …] tal cual', () => {
    expect(html('Domicilio: [COMPLETAR domicilio legal].').textContent).toContain('[COMPLETAR domicilio legal]');
  });
});
