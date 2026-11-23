import { beforeEach, describe, expect, it } from 'vitest';
import fuenteJson from '../contenido/seguridad.json';
import { afirmaciones, textoPlano, type FuenteSeguridad } from '../features/seguridad/fuente';
import { montarSeguridad } from './seguridad';

const fuente = fuenteJson as FuenteSeguridad;
const normalizar = (t: string | null | undefined) => (t ?? '').replace(/\s+/g, ' ').trim();

describe('3.2 página /seguridad armada desde la fuente', () => {
  let contenedor: HTMLElement;

  beforeEach(() => {
    document.body.innerHTML = '<main id="raiz"></main>';
    contenedor = document.querySelector<HTMLElement>('#raiz') as HTMLElement;
    montarSeguridad(contenedor);
  });

  it('tiene un solo h1, la bajada y la fecha de la fuente en es-AR', () => {
    expect(contenedor.querySelectorAll('h1')).toHaveLength(1);
    expect(contenedor.querySelector('h1')?.textContent).toBe('Seguridad y datos');
    const fecha = contenedor.querySelector('time');
    expect(fecha?.getAttribute('datetime')).toBe('2026-11-22');
    expect(normalizar(fecha?.parentElement?.textContent)).toContain('Última actualización: 22 de noviembre de 2026.');
  });

  it('lo primero después del título y la fecha es el aviso de "sin revisión de un abogado", con el link a lo que falta', () => {
    const aviso = contenedor.querySelector('[data-aviso-legal]');
    expect(aviso).not.toBeNull();
    expect(normalizar(aviso?.textContent)).toContain('todavía no tuvieron revisión de un abogado');
    expect(aviso?.querySelector('a')?.getAttribute('href')).toBe('#falta');
    const nav = contenedor.querySelector('nav[aria-labelledby]');
    expect(aviso?.compareDocumentPosition(nav as Node) ?? 0).toBe(Node.DOCUMENT_POSITION_FOLLOWING);
  });

  it('el índice lleva a los ocho temas, cada uno con su h2', () => {
    const enlaces = [...contenedor.querySelectorAll<HTMLAnchorElement>('nav[aria-labelledby] a')];
    expect(enlaces.map((a) => a.getAttribute('href'))).toEqual(fuente.temas.map((t) => `#${t.id}`));
    for (const tema of fuente.temas) {
      const seccion = contenedor.querySelector(`section#${tema.id}`);
      expect(seccion?.querySelector('h2')?.textContent).toBe(tema.titulo);
    }
  });

  it('cada afirmación de la fuente aparece en la página, y su respaldo no', () => {
    const pagina = normalizar(contenedor.textContent);
    for (const afirmacion of afirmaciones(fuente)) expect(pagina).toContain(normalizar(textoPlano(afirmacion.texto)));
    for (const respaldo of ['RN-AD-02', 'MigracionEventoAuditoriaTest', 'EnmascaradorDePii', 'DD-40', 'C-18']) expect(pagina).not.toContain(respaldo);
  });

  it('la tabla de roles tiene encabezados de fila y "Lo que todavía no está" marca cada ítem con ícono y texto', () => {
    expect([...contenedor.querySelectorAll('#roles th[scope="row"]')].map((th) => th.textContent)).toEqual(['Tu consultorio', 'Nosotros, con tus pacientes', 'Nosotros, con tu cuenta']);
    const faltan = [...contenedor.querySelectorAll('#falta li')];
    expect(faltan.map((li) => li.querySelector('strong')?.textContent)).toEqual([
      'Revisión legal profesional.',
      'Verificación en dos pasos.',
      'Certificaciones.',
      'Servidor contratado.',
      'Baja de la cuenta.',
    ]);
    for (const li of faltan) expect(li.querySelector('svg[aria-hidden="true"]')).not.toBeNull();
  });

  it('los datos que faltan definir se ven como (TBD), a la vista', () => {
    const marcadores = [...contenedor.querySelectorAll('[data-tbd]')].map((e) => e.textContent);
    expect(marcadores).toEqual(['(TBD) email de seguridad', '(TBD) proveedor del VPS', '(TBD) proveedor SMTP']);
  });

  it('el pie enlaza a precios, términos y privacidad', () => {
    const pie = contenedor.querySelector('nav[aria-label="Legales"]');
    expect([...(pie?.querySelectorAll('a') ?? [])].map((a) => a.getAttribute('href'))).toEqual(['/precios', '/terminos', '/privacidad']);
  });
});

describe('3.3 accesibilidad', () => {
  beforeEach(() => {
    document.body.innerHTML = '<main id="raiz"></main>';
    montarSeguridad(document.querySelector<HTMLElement>('#raiz') as HTMLElement);
  });

  it('las secciones se nombran por su título y la jerarquía no saltea niveles', () => {
    for (const seccion of document.querySelectorAll('section')) {
      const id = seccion.getAttribute('aria-labelledby');
      expect(id === null ? null : document.getElementById(id)?.tagName).toBe('H2');
    }
    expect(document.querySelector('h3, h4')).toBeNull();
  });

  it('los links del índice y del pie tienen área táctil de al menos 44 px (min-h-touch)', () => {
    const enlaces = [...document.querySelectorAll('nav a')];
    expect(enlaces.length).toBe(11);
    for (const a of enlaces) expect(a.className).toContain('min-h-touch');
  });

  it('la tabla de roles se apila en mobile', () => {
    const tabla = document.querySelector('#roles table');
    // Mismo corte que el mockup aprobado (hasta 519 px).
    expect(tabla?.className).toContain('max-[519px]:block');
    expect(tabla?.querySelector('thead')?.className).toContain('max-[519px]:sr-only');
  });
});
