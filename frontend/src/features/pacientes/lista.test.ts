import { afterEach, describe, expect, it } from 'vitest';
import { botonConTexto, crearContextoDePagina, esperar, json, problema, type Guion } from '../configuracion/prueba-contexto';
import { dejarAvisoDeRevision } from './aviso-de-revision';
import { montarListaDePacientes } from './lista';

// C-17, tarea 4.2 (spec pantalla-pacientes, mockup aprobado #36): lista con filtro, "Mostrar más" y estados.
// Datos ficticios (regla dura 17).

const ANA = {
  id: 'p-1', nombre: 'Ana', apellido: 'Ejemplo', dni: '90000012', telefono: '+5491100000012', email: 'ana.ejemplo@example.com', requiereRevision: true,
  informado: { nombre: 'Anna', apellido: 'Ejemplo', email: null, informadoAt: '2026-11-18T13:00:00Z', turno: null },
};
const DARIO = { id: 'p-4', nombre: 'Darío', apellido: 'Demo', dni: '90000045', telefono: '+5491100000045', email: null, requiereRevision: false, informado: null };
const listado = (revision: boolean, pagina = 0) => `GET /api/v1/pacientes/listado?requiereRevision=${revision}&page=${pagina}&size=20`;

async function montar(guion: Guion, ruta = '/app/pacientes') {
  history.replaceState(null, '', ruta);
  const preparado = crearContextoDePagina(guion);
  montarListaDePacientes(preparado.contexto);
  await esperar();
  await esperar();
  return preparado;
}

const filtro = (contenedor: HTMLElement, texto: string) =>
  [...contenedor.querySelectorAll<HTMLButtonElement>('[role="group"] button')].find((b) => (b.textContent ?? '').startsWith(texto)) ?? null;
const textoDe = (e: Element | null | undefined) => (e?.textContent ?? '').replace(/\s+/g, ' ').trim();

afterEach(() => history.replaceState(null, '', '/'));

describe('lista de pacientes', () => {
  it('desde el aviso de la agenda abre con "Requieren revisión" activo y la línea de lo escrito', async () => {
    const { contenedor } = await montar({ [listado(true)]: [json({ items: [ANA], total: 3 })] }, '/app/pacientes?requiereRevision=true');

    expect(filtro(contenedor, 'Requieren revisión')?.getAttribute('aria-pressed')).toBe('true');
    expect(filtro(contenedor, 'Todos')?.getAttribute('aria-pressed')).toBe('false');
    expect(textoDe(filtro(contenedor, 'Requieren revisión'))).toBe('Requieren revisión3 (3 para revisar)');
    expect(textoDe(contenedor.querySelector('[data-resumen]'))).toBe('3 pacientes con datos para revisar');
    const fila = contenedor.querySelector('li[data-paciente="p-1"]');
    expect(textoDe(fila?.querySelector('[data-nombre]'))).toBe('Ejemplo, Ana Revisar');
    expect(fila?.querySelector('[data-etiqueta-revisar] svg')).not.toBeNull();
    expect(textoDe(fila)).toContain('DNI 90.000.012');
    expect(textoDe(fila)).toContain('Teléfono +54 9 11 0000-0012');
    expect(textoDe(fila)).toContain('Email ana.ejemplo@example.com');
    expect(textoDe(fila)).toContain('Escribió «Anna Ejemplo» al reservar el 18 de noviembre');
    const revisar = fila?.querySelector('a');
    expect(revisar?.getAttribute('href')).toBe('/app/pacientes/p-1');
    expect(revisar?.getAttribute('aria-label')).toBe('Revisar a Ana Ejemplo');
  });

  it('sin el parámetro muestra todos, ordenados por apellido, con "Ver"', async () => {
    const { contenedor } = await montar({ [listado(false)]: [json({ items: [DARIO, ANA], total: 2 })] });

    expect(filtro(contenedor, 'Todos')?.getAttribute('aria-pressed')).toBe('true');
    expect(textoDe(contenedor.querySelector('[data-resumen]'))).toBe('2 pacientes · ordenados por apellido');
    const ver = contenedor.querySelector('li[data-paciente="p-4"] a');
    expect(ver?.textContent).toBe('Ver');
    expect(ver?.getAttribute('aria-label')).toBe('Ver a Darío Demo');
    expect(contenedor.querySelector('li[data-paciente="p-4"] [data-etiqueta-revisar]')).toBeNull();
    expect(botonConTexto(contenedor, 'Mostrar más pacientes')).toBeNull();
  });

  it('cambiar de filtro vuelve a pedir la lista y actualiza la URL', async () => {
    const { contenedor, llamadas } = await montar({ [listado(false)]: [json({ items: [DARIO], total: 1 })], [listado(true)]: [json({ items: [ANA], total: 1 })] });

    filtro(contenedor, 'Requieren revisión')?.click();
    await esperar();
    await esperar();

    expect(llamadas.map((l) => l.url)).toContain('/api/v1/pacientes/listado?requiereRevision=true&page=0&size=20');
    expect(location.search).toBe('?requiereRevision=true');
    expect(contenedor.querySelector('li[data-paciente="p-1"]')).not.toBeNull();
  });

  it('"Mostrar más pacientes" agrega la página siguiente y desaparece al terminar', async () => {
    const primera = Array.from({ length: 20 }, (_, i) => ({ ...DARIO, id: `p-${i + 10}` }));
    const { contenedor } = await montar({ [listado(false)]: [json({ items: primera, total: 21 })], [listado(false, 1)]: [json({ items: [{ ...DARIO, id: 'p-99' }], total: 21 })] });

    botonConTexto(contenedor, 'Mostrar más pacientes')?.click();
    await esperar();
    await esperar();

    expect(contenedor.querySelectorAll('li[data-paciente]')).toHaveLength(21);
    expect(botonConTexto(contenedor, 'Mostrar más pacientes')).toBeNull();
  });

  it('nada para revisar ofrece ver todos', async () => {
    const { contenedor } = await montar({ [listado(true)]: [json({ items: [], total: 0 })], [listado(false)]: [json({ items: [DARIO], total: 1 })] }, '/app/pacientes?requiereRevision=true');

    expect(textoDe(contenedor.querySelector('h2'))).toBe('No hay pacientes para revisar');
    contenedor.querySelector<HTMLButtonElement>('section button')?.click();
    await esperar();
    await esperar();
    expect(contenedor.querySelector('li[data-paciente="p-4"]')).not.toBeNull();
  });

  it('sin pacientes todavía', async () => {
    const { contenedor } = await montar({ [listado(false)]: [json({ items: [], total: 0 })] });
    expect(textoDe(contenedor.querySelector('h2'))).toBe('Todavía no tenés pacientes');
  });

  it('un error al cargar ofrece reintentar', async () => {
    const { contenedor } = await montar({ [listado(false)]: [problema(500, 'ERROR_INTERNO'), json({ items: [DARIO], total: 1 })] });
    expect(textoDe(contenedor.querySelector('h2'))).toBe('No pudimos cargar tus pacientes');
    botonConTexto(contenedor, 'Reintentar')?.click();
    await esperar();
    await esperar();
    expect(contenedor.querySelector('li[data-paciente="p-4"]')).not.toBeNull();
  });

  it('sin conexión avisa que la lista no se guarda en el dispositivo', async () => {
    const sinRed = () => {
      throw new TypeError('Failed to fetch');
    };
    const { contenedor } = await montar({ [listado(false)]: [sinRed] });
    expect(textoDe(contenedor)).toContain('La lista de pacientes no se guarda en el dispositivo.');
  });

  it('muestra un esqueleto mientras carga', () => {
    history.replaceState(null, '', '/app/pacientes');
    const { contexto, contenedor } = crearContextoDePagina({ [listado(false)]: [() => new Promise<Response>(() => undefined)] });
    montarListaDePacientes(contexto);
    expect(contenedor.querySelector('[aria-busy="true"]')).not.toBeNull();
  });

  it('después de una revisión muestra el aviso "Listo" una sola vez', async () => {
    dejarAvisoDeRevision('Listo: Ejemplo, Ana quedó revisada.');
    await montar({ [listado(true)]: [json({ items: [], total: 0 })] }, '/app/pacientes?requiereRevision=true');
    expect(textoDe(document.querySelector('[role="status"][data-aviso-revision]'))).toBe('Listo: Ejemplo, Ana quedó revisada.');

    await montar({ [listado(true)]: [json({ items: [], total: 0 })] }, '/app/pacientes?requiereRevision=true');
    expect(document.querySelectorAll('[data-aviso-revision]')).toHaveLength(0);
  });
});
