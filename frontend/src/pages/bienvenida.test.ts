import { afterEach, describe, expect, it, vi } from 'vitest';
import { botonConTexto, crearContextoDePagina, escribir, esperar, json, problema, type Guion } from '../features/configuracion/prueba-contexto';
import { pagina } from './bienvenida';

// Tareas 4.2 a 4.4 de C-09 (spec app-configuracion/onboarding; mockup `bienvenida.html`). Datos ficticios.

const CONSULTORIO = {
  nombre: 'Consultorio Demo Sur',
  slug: 'consultorio-demo-sur',
  telefonoContacto: '',
  zonaHoraria: 'America/Argentina/Buenos_Aires',
  anticipacionCancelacionMin: 1440,
  anticipacionReservaMin: 120,
  horizonteReservaDias: 60,
  pasoHuecosMin: 15,
  reservaPublicaActiva: true,
};
const CON_TELEFONO = { ...CONSULTORIO, telefonoContacto: '+5491100000001' };
const ACTIVAS = '/api/v1/prestaciones';
const consulta = { id: 'p-consulta', nombre: 'Consulta', duracionMin: 30, activa: true, visiblePublica: true, orden: 1 };
const franja = (diaSemana: number, horaInicio: string, horaFin: string) => ({ diaSemana, horaInicio, horaFin });

async function montar(guion: Guion) {
  const prueba = crearContextoDePagina({ 'GET /api/v1/consultorio': [json(CONSULTORIO)], ...guion });
  pagina.montar(prueba.contexto);
  await esperar();
  const campo = (nombre: string) => prueba.contenedor.querySelector<HTMLInputElement>(`[name="${nombre}"]`);
  const siguiente = async () => {
    botonConTexto(prueba.contenedor, 'Siguiente')?.click();
    await esperar();
    await esperar();
  };
  return { ...prueba, campo, siguiente };
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('asistente de primer ingreso', () => {
  it('se muestra sin la navegación del shell, con "Terminar después" a la agenda', () => {
    expect(pagina.sinShell).toBe(true);
  });

  it('paso 1: precarga nombre y slug del registro y muestra "Paso 1 de 4"', async () => {
    const { contenedor, campo } = await montar({});

    expect(contenedor.textContent).toContain('Paso 1 de 4');
    expect(campo('nombre')?.value).toBe('Consultorio Demo Sur');
    expect(campo('slug')?.value).toBe('consultorio-demo-sur');
    expect(contenedor.querySelector('a[href="/app/agenda"]')?.textContent).toBe('Terminar después');
  });

  it('paso 1 sin teléfono: no avanza, no envía y marca el campo', async () => {
    const { contenedor, campo, llamadas, siguiente } = await montar({});

    await siguiente();

    expect(llamadas.filter((l) => l.metodo === 'PUT')).toHaveLength(0);
    expect(contenedor.textContent).toContain('Paso 1 de 4');
    expect(campo('telefonoContacto')?.getAttribute('aria-invalid')).toBe('true');
    expect(contenedor.textContent).toContain('Poné un teléfono de contacto');
  });

  it('paso 1 guardado: envía la configuración completa y pasa al paso 2 con el 1 completo', async () => {
    const { contenedor, campo, llamadas, siguiente } = await montar({
      'PUT /api/v1/consultorio': [json(CON_TELEFONO)],
      [`GET ${ACTIVAS}`]: [json([])],
    });
    escribir(campo('telefonoContacto'), '+54 9 11 0000-0001');

    await siguiente();

    expect(llamadas.find((l) => l.metodo === 'PUT')?.cuerpo).toMatchObject({ nombre: 'Consultorio Demo Sur', slug: 'consultorio-demo-sur', telefonoContacto: '+54 9 11 0000-0001', anticipacionCancelacionMin: 1440 });
    expect(contenedor.textContent).toContain('Paso 2 de 4');
    const completo = contenedor.querySelector('ol li:first-child');
    expect(completo?.textContent).toContain('(completo)');
    expect(completo?.querySelector('svg')).not.toBeNull();
    expect(contenedor.querySelector('ol [aria-current="step"]')?.textContent).toContain('Prestaciones');
  });

  it('paso 1 con slug tomado: se queda, marca el slug y ofrece las sugerencias', async () => {
    const { contenedor, campo, siguiente } = await montar({
      'PUT /api/v1/consultorio': [problema(409, 'SLUG_EN_USO', { sugerencias: ['dra-ejemplo-2'] })],
    });
    escribir(campo('telefonoContacto'), '+54 9 11 0000-0001');
    escribir(campo('slug'), 'dra-ejemplo');

    await siguiente();

    expect(contenedor.textContent).toContain('Paso 1 de 4');
    expect(campo('slug')?.getAttribute('aria-invalid')).toBe('true');
    expect(botonConTexto(contenedor, 'dra-ejemplo-2')).not.toBeNull();
  });

  it('paso 1 sin cambios y válido avanza sin volver a enviarlo (D3)', async () => {
    const { contenedor, llamadas, siguiente } = await montar({
      'GET /api/v1/consultorio': [json(CON_TELEFONO)],
      [`GET ${ACTIVAS}`]: [json([])],
    });

    await siguiente();

    expect(llamadas.filter((l) => l.metodo === 'PUT')).toHaveLength(0);
    expect(contenedor.textContent).toContain('Paso 2 de 4');
  });

  it('paso 2: un atajo crea la prestación y sin ninguna no deja avanzar', async () => {
    const limpieza = { id: 'p-limpieza', nombre: 'Limpieza', duracionMin: 45, activa: true, visiblePublica: true, orden: 1 };
    const { contenedor, llamadas, siguiente } = await montar({
      'GET /api/v1/consultorio': [json(CON_TELEFONO)],
      [`GET ${ACTIVAS}`]: [json([])],
      'POST /api/v1/prestaciones': [json(limpieza, 201)],
    });
    await siguiente();

    await siguiente();
    expect(contenedor.textContent).toContain('Paso 2 de 4');
    expect(contenedor.querySelector('[role="alert"]')?.textContent).toContain('Agregá al menos una prestación para seguir.');

    botonConTexto(contenedor, 'Limpieza · 45 min')?.click();
    await esperar();

    expect(llamadas.find((l) => l.metodo === 'POST')?.cuerpo).toMatchObject({ nombre: 'Limpieza', duracionMin: 45 });
    expect(contenedor.querySelector('[aria-label="Tus prestaciones"]')?.textContent).toContain('Limpieza');
  });

  it('paso 2: un atajo que ya existe no es un error', async () => {
    const { contenedor, siguiente } = await montar({
      'GET /api/v1/consultorio': [json(CON_TELEFONO)],
      [`GET ${ACTIVAS}`]: [json([consulta]), json([consulta])],
      'POST /api/v1/prestaciones': [problema(409, 'PRESTACION_DUPLICADA')],
    });
    await siguiente();

    botonConTexto(contenedor, 'Consulta · 30 min')?.click();
    await esperar();

    expect(contenedor.querySelector('[role="alert"]')).toBeNull();
    expect(contenedor.textContent).toContain('(agregada)');
  });

  it('paso 3: el atajo carga lunes a viernes sin guardar, y sin franjas no avanza', async () => {
    const { contenedor, llamadas, siguiente } = await montar({
      'GET /api/v1/consultorio': [json(CON_TELEFONO)],
      [`GET ${ACTIVAS}`]: [json([consulta])],
      'GET /api/v1/disponibilidad': [json({ franjas: [] })],
      'PUT /api/v1/disponibilidad': [json({ franjas: [] })],
    });
    await siguiente();
    await siguiente();
    expect(contenedor.textContent).toContain('Paso 3 de 4');

    await siguiente();
    expect(contenedor.textContent).toContain('Paso 3 de 4');
    expect(contenedor.textContent).toContain('Cargá al menos una franja para seguir.');
    expect(llamadas.filter((l) => l.url === '/api/v1/disponibilidad' && l.metodo === 'PUT')).toHaveLength(0);

    botonConTexto(contenedor, 'Lunes a viernes, 9 a 13 y 15 a 19')?.click();
    const lunes = [...(contenedor.querySelector('[data-dia="1"]')?.querySelectorAll<HTMLInputElement>('input[type="time"]') ?? [])].map((i) => i.value);
    expect(lunes).toEqual(['09:00', '13:00', '15:00', '19:00']);
    expect(llamadas.filter((l) => l.url === '/api/v1/disponibilidad' && l.metodo === 'PUT')).toHaveLength(0);
  });

  it('paso 4: copia el link con "¡Copiado!" anunciado y oculta "Compartir" sin Web Share', async () => {
    const escribirPortapapeles = vi.fn(async () => undefined);
    vi.stubGlobal('navigator', { ...navigator, clipboard: { writeText: escribirPortapapeles }, share: undefined });
    const { contenedor, siguiente } = await montar({
      'GET /api/v1/consultorio': [json(CON_TELEFONO)],
      [`GET ${ACTIVAS}`]: [json([consulta])],
      'GET /api/v1/disponibilidad': [json({ franjas: [franja(1, '09:00', '13:00')] })],
    });
    await siguiente();
    await siguiente();
    await siguiente();

    expect(contenedor.textContent).toContain('Paso 4 de 4');
    expect(contenedor.textContent).toContain(`${location.origin}/c/consultorio-demo-sur`);
    expect(botonConTexto(contenedor, 'Compartir')).toBeNull();
    expect(contenedor.textContent).toContain('Tu navegador no permite compartir directo');
    botonConTexto(contenedor, 'Copiar link')?.click();
    await esperar();

    expect(escribirPortapapeles).toHaveBeenCalledWith(`${location.origin}/c/consultorio-demo-sur`);
    const copiado = botonConTexto(contenedor, '¡Copiado!');
    expect(copiado?.getAttribute('aria-live')).toBe('polite');
    const visibles = [...contenedor.querySelectorAll<HTMLAnchorElement>('a[href="/app/agenda"]')].filter((a) => !a.hidden);
    expect(visibles.map((a) => a.textContent)).toEqual(['Ir a mi agenda']);
  });

  it('paso 4: con Web Share muestra "Compartir"', async () => {
    const compartir = vi.fn(async () => undefined);
    vi.stubGlobal('navigator', { ...navigator, share: compartir });
    const { contenedor, siguiente } = await montar({
      'GET /api/v1/consultorio': [json(CON_TELEFONO)],
      [`GET ${ACTIVAS}`]: [json([consulta])],
      'GET /api/v1/disponibilidad': [json({ franjas: [franja(1, '09:00', '13:00')] })],
    });
    await siguiente();
    await siguiente();
    await siguiente();

    botonConTexto(contenedor, 'Compartir')?.click();

    expect(compartir).toHaveBeenCalledWith(expect.objectContaining({ url: `${location.origin}/c/consultorio-demo-sur` }));
  });
});
