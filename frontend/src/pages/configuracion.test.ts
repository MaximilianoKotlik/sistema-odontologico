import { afterEach, describe, expect, it, vi } from 'vitest';
import { botonConTexto, crearContextoDePagina, enviar, escribir, esperar, json, problema, type Guion } from '../features/configuracion/prueba-contexto';
import { pagina } from './configuracion';

// Tareas 3.1 y 3.2 de C-09 (spec app-configuracion/consultorio; mockup `consultorio.html`). Datos ficticios.

const CONSULTORIO = {
  nombre: 'Consultorio Demo Sur',
  slug: 'consultorio-demo-sur',
  direccion: 'Calle Falsa 123, CABA',
  telefonoContacto: '+5491100000001',
  zonaHoraria: 'America/Argentina/Buenos_Aires',
  anticipacionCancelacionMin: 1440,
  anticipacionReservaMin: 120,
  horizonteReservaDias: 60,
  pasoHuecosMin: 15,
  reservaPublicaActiva: true,
  plan: 'PRUEBA',
  pruebaHasta: '2026-12-21',
};

async function montar(extra: Guion = {}) {
  const prueba = crearContextoDePagina({ 'GET /api/v1/consultorio': [json(CONSULTORIO)], ...extra });
  pagina.montar(prueba.contexto);
  await esperar();
  const campo = (nombre: string) => prueba.contenedor.querySelector<HTMLInputElement>(`[name="${nombre}"]`);
  return { ...prueba, campo };
}

const textoDelError = (contenedor: HTMLElement, input: HTMLElement | null) => {
  const ids = input?.getAttribute('aria-describedby')?.split(' ') ?? [];
  return ids.map((id) => contenedor.querySelector(`#${id}`)?.textContent ?? '').join(' ');
};

const barraVisible = (contenedor: HTMLElement) => contenedor.querySelector<HTMLElement>('[data-barra-guardado]')?.hidden === false;

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('Configuración: pestañas', () => {
  it('muestra las tres pestañas como links y marca Consultorio como actual', async () => {
    const { contenedor } = await montar();
    const pestanas = [...contenedor.querySelectorAll<HTMLAnchorElement>('nav[aria-label="Secciones de configuración"] a')];

    expect(pestanas.map((a) => [a.textContent, a.getAttribute('href')])).toEqual([
      ['Consultorio', '/app/configuracion'],
      ['Prestaciones', '/app/configuracion/prestaciones'],
      ['Horarios', '/app/configuracion/horarios'],
    ]);
    expect(pestanas.every((a) => a.hasAttribute('data-link'))).toBe(true);
    expect(pestanas.filter((a) => a.getAttribute('aria-current') === 'page').map((a) => a.textContent)).toEqual(['Consultorio']);
  });
});

describe('Configuración: consultorio', () => {
  it('mientras carga muestra un esqueleto con aria-busy', () => {
    const prueba = crearContextoDePagina({ 'GET /api/v1/consultorio': [() => new Promise<Response>(() => undefined)] });
    pagina.montar(prueba.contexto);
    expect(prueba.contenedor.querySelector('[aria-busy="true"]')).not.toBeNull();
  });

  it('muestra los valores actuales con unidades legibles', async () => {
    const { campo } = await montar();

    expect(campo('nombre')?.value).toBe('Consultorio Demo Sur');
    expect(campo('anticipacionCancelacion')?.value).toBe('24');
    expect(campo('anticipacionReserva')?.value).toBe('2');
    expect(campo('horizonteReservaDias')?.value).toBe('60');
    expect(campo('pasoHuecosMin')?.value).toBe('15');
  });

  it('el link público usa el origen real y se puede copiar', async () => {
    const escribirPortapapeles = vi.fn(async () => undefined);
    vi.stubGlobal('navigator', { ...navigator, clipboard: { writeText: escribirPortapapeles } });
    const { contenedor } = await montar();

    expect(contenedor.querySelector('[data-link-publico]')?.textContent).toBe(`${location.origin}/c/consultorio-demo-sur`);
    botonConTexto(contenedor, 'Copiar link')?.click();
    await esperar();

    expect(escribirPortapapeles).toHaveBeenCalledWith(`${location.origin}/c/consultorio-demo-sur`);
    expect(contenedor.querySelector('[aria-live="polite"]')?.textContent).toContain('¡Copiado!');
  });

  it('sin cambios no hay barra de guardado; con cambios aparece y bloquea la salida', async () => {
    const { contenedor, campo, bloqueada } = await montar();
    expect(barraVisible(contenedor)).toBe(false);

    escribir(campo('nombre'), 'Consultorio Demo Norte');

    expect(barraVisible(contenedor)).toBe(true);
    expect(contenedor.querySelector('[data-barra-guardado]')?.textContent).toContain('Tenés cambios sin guardar');
    expect(bloqueada()).toBe(true);
  });

  it('"Descartar" vuelve a los valores guardados', async () => {
    const { contenedor, campo, bloqueada } = await montar();
    escribir(campo('nombre'), 'Consultorio Demo Norte');

    botonConTexto(contenedor, 'Descartar')?.click();

    expect(campo('nombre')?.value).toBe('Consultorio Demo Sur');
    expect(barraVisible(contenedor)).toBe(false);
    expect(bloqueada()).toBe(false);
  });

  it('guarda 48 horas como 2880 minutos y anuncia "Guardamos los cambios"', async () => {
    const guardado = { ...CONSULTORIO, anticipacionCancelacionMin: 2880 };
    const { contenedor, campo, llamadas, bloqueada } = await montar({ 'PUT /api/v1/consultorio': [json(guardado)] });
    escribir(campo('anticipacionCancelacion'), '48');

    enviar(contenedor);
    await esperar();

    const put = llamadas.find((l) => l.metodo === 'PUT');
    expect(put?.cuerpo).toMatchObject({ anticipacionCancelacionMin: 2880, slug: 'consultorio-demo-sur' });
    expect(contenedor.querySelector('[aria-live="polite"]')?.textContent).toContain('Guardamos los cambios');
    expect(campo('anticipacionCancelacion')?.value).toBe('48');
    expect(bloqueada()).toBe(false);
  });

  it('el slug se normaliza al escribir y la vista previa avisa que el link anterior deja de funcionar', async () => {
    const { contenedor, campo } = await montar();

    escribir(campo('slug'), 'Dra Ejemplo');

    expect(campo('slug')?.value).toBe('dra-ejemplo');
    expect(contenedor.querySelector('[data-aviso-slug]')?.textContent).toContain(`${location.origin}/c/dra-ejemplo`);
    expect(contenedor.querySelector('[data-aviso-slug]')?.textContent).toContain('el link anterior deja de funcionar');
  });

  it('slug en uso: marca el campo y las sugerencias se eligen con un toque, sin guardar', async () => {
    const { contenedor, campo, llamadas } = await montar({
      'PUT /api/v1/consultorio': [problema(409, 'SLUG_EN_USO', { sugerencias: ['dra-ejemplo-2', 'dra-ejemplo-3'] })],
    });
    escribir(campo('slug'), 'dra-ejemplo');
    enviar(contenedor);
    await esperar();

    expect(campo('slug')?.getAttribute('aria-invalid')).toBe('true');
    botonConTexto(contenedor, 'dra-ejemplo-2')?.click();

    expect(campo('slug')?.value).toBe('dra-ejemplo-2');
    expect(campo('slug')?.getAttribute('aria-invalid')).toBeNull();
    expect(llamadas.filter((l) => l.metodo === 'PUT')).toHaveLength(1);
  });

  it('slug reservado: el campo dice que esa dirección está reservada', async () => {
    const { contenedor, campo } = await montar({ 'PUT /api/v1/consultorio': [problema(400, 'SLUG_RESERVADO')] });
    escribir(campo('slug'), 'admin');
    enviar(contenedor);
    await esperar();

    expect(textoDelError(contenedor, campo('slug'))).toContain('reservada');
  });

  it('horizonte fuera de rango: no envía y el resumen enfocado lleva al campo', async () => {
    const { contenedor, campo, llamadas } = await montar();
    escribir(campo('horizonteReservaDias'), '400');

    enviar(contenedor);
    await esperar();

    expect(llamadas.filter((l) => l.metodo === 'PUT')).toHaveLength(0);
    expect(campo('horizonteReservaDias')?.getAttribute('aria-invalid')).toBe('true');
    const resumen = contenedor.querySelector<HTMLElement>('[data-resumen-errores]');
    expect(document.activeElement).toBe(resumen);
    expect(resumen?.querySelector('a')?.getAttribute('href')).toBe(`#${campo('horizonteReservaDias')?.id ?? ''}`);
  });

  it('teléfono vacío: no envía y el campo dice que es obligatorio', async () => {
    const { contenedor, campo, llamadas } = await montar();
    escribir(campo('telefonoContacto'), '');

    enviar(contenedor);
    await esperar();

    expect(llamadas.filter((l) => l.metodo === 'PUT')).toHaveLength(0);
    expect(textoDelError(contenedor, campo('telefonoContacto'))).toContain('Poné un teléfono de contacto');
  });

  it('anticipación igual o mayor que el horizonte: el mensaje va en su campo', async () => {
    const { contenedor, campo } = await montar();
    escribir(campo('anticipacionReserva'), '1500');

    enviar(contenedor);
    await esperar();

    expect(textoDelError(contenedor, campo('anticipacionReserva'))).toContain('menor que el horizonte (60 días = 1440 horas)');
  });

  it('teléfono inválido según la API: mensaje con un ejemplo y lo escrito se conserva', async () => {
    const { contenedor, campo } = await montar({ 'PUT /api/v1/consultorio': [problema(400, 'TELEFONO_INVALIDO')] });
    escribir(campo('telefonoContacto'), '123');
    enviar(contenedor);
    await esperar();

    expect(textoDelError(contenedor, campo('telefonoContacto'))).toContain('por ejemplo 11 0000-0001');
    expect(campo('telefonoContacto')?.value).toBe('123');
  });

  it('el interruptor de reserva pública dice su estado en texto y pausarla envía false', async () => {
    const { contenedor, llamadas } = await montar({ 'PUT /api/v1/consultorio': [json({ ...CONSULTORIO, reservaPublicaActiva: false })] });
    const interruptor = contenedor.querySelector<HTMLButtonElement>('[role="switch"]');
    expect(interruptor?.getAttribute('aria-checked')).toBe('true');
    expect(interruptor?.textContent).toContain('Activa');

    interruptor?.click();
    expect(interruptor?.getAttribute('aria-checked')).toBe('false');
    expect(interruptor?.textContent).toContain('Pausada');
    expect(contenedor.textContent).toContain('los pacientes no pueden reservar desde el link');
    enviar(contenedor);
    await esperar();

    expect(llamadas.find((l) => l.metodo === 'PUT')?.cuerpo).toMatchObject({ reservaPublicaActiva: false });
  });

  it('conflicto de concurrencia: avisa y "Ver los valores actuales" vuelve a pedirlos', async () => {
    const actuales = { ...CONSULTORIO, nombre: 'Consultorio Demo Centro' };
    const { contenedor, campo, llamadas } = await montar({
      'PUT /api/v1/consultorio': [problema(409, 'CONFLICTO_CONCURRENCIA')],
      'GET /api/v1/consultorio': [json(CONSULTORIO), json(actuales)],
    });
    escribir(campo('nombre'), 'Consultorio Demo Norte');
    enviar(contenedor);
    await esperar();

    expect(contenedor.querySelector('[role="alert"]')?.textContent).toContain('cambió en otra pestaña o dispositivo');
    botonConTexto(contenedor, 'Ver los valores actuales')?.click();
    await esperar();

    expect(llamadas.filter((l) => l.metodo === 'GET')).toHaveLength(2);
    expect(campo('nombre')?.value).toBe('Consultorio Demo Centro');
  });

  it('si no carga, muestra un error con "Reintentar"', async () => {
    const prueba = crearContextoDePagina({ 'GET /api/v1/consultorio': [problema(500, 'ERROR_INESPERADO'), json(CONSULTORIO)] });
    pagina.montar(prueba.contexto);
    await esperar();

    botonConTexto(prueba.contenedor, 'Reintentar')?.click();
    await esperar();

    expect(prueba.contenedor.querySelector<HTMLInputElement>('[name="nombre"]')?.value).toBe('Consultorio Demo Sur');
  });
});
