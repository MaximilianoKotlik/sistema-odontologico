import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import '../shared/prueba-blob';
import { crearContextoDePagina, esperar, json, problema, type Guion, type Respuesta } from '../features/configuracion/prueba-contexto';
import { pagina } from './exportar';

// Tareas 8.2 a 8.4 de C-19 (spec pantalla-exportacion; mockup `exportar.html`). Datos ficticios.

const RUTA = 'POST /api/v1/exportacion';
const NOMBRE = 'export-demo-2026-11-20.zip';
/** Fin de un ZIP vacío válido: la firma del directorio central y 18 bytes en cero. */
const FIN_DE_ZIP = new Uint8Array([0x50, 0x4b, 0x05, 0x06, ...new Array<number>(18).fill(0)]);

const zip = (bytes: Uint8Array<ArrayBuffer> = FIN_DE_ZIP) => () =>
  new Response(bytes, {
    status: 200,
    headers: { 'Content-Type': 'application/zip', 'Content-Disposition': `attachment; filename="${NOMBRE}"` },
  });

const guardados: string[] = [];

async function montar(respuestas: Respuesta[], plan = 'ACTIVO', extra: Guion = {}) {
  const prueba = crearContextoDePagina({ 'GET /api/v1/consultorio': [json({ plan })], [RUTA]: respuestas, ...extra });
  pagina.montar(prueba.contexto);
  await esperar();
  const accion = () => prueba.contenedor.querySelector<HTMLButtonElement>('button[data-exportar]');
  const region = () => prueba.contenedor.querySelector<HTMLElement>('[aria-live="polite"]');
  return { ...prueba, accion, region };
}

async function exportar(boton: HTMLButtonElement | null) {
  boton?.click();
  for (let i = 0; i < 5; i += 1) await esperar();
}

beforeEach(() => {
  guardados.length = 0;
  vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: vi.fn(() => 'blob:ficticio'), revokeObjectURL: vi.fn() }));
  vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
    guardados.push(this.download);
  });
});

afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

describe('Exportar: contenido', () => {
  it('explica qué incluye y ofrece el botón, sin la sección pendiente', async () => {
    const { contenedor, accion } = await montar([]);
    const texto = contenedor.textContent ?? '';

    expect(accion()?.textContent).toContain('Exportar mis datos');
    expect(texto).toContain('Planillas CSV y archivos JSON');
    expect(texto).toContain('Un resumen en PDF');
    expect(texto).toContain('manifest.json');
    expect(texto).toContain('No incluye tu contraseña ni los links privados de tus pacientes.');
    expect(texto).toContain('Podés exportar siempre, también si tu plan venció.');
    expect(texto).toContain('guardalo en un lugar seguro');
    expect(texto).not.toContain('Esta sección todavía no está disponible');
  });

  it('con el plan vencido avisa que la exportación funciona igual', async () => {
    const { contenedor } = await montar([], 'VENCIDO');

    expect(contenedor.textContent).toContain('Tu prueba terminó, pero tus datos siguen siendo tuyos');
  });

  it('el botón queda habilitado en solo lectura (RN-EX-03)', async () => {
    const { accion } = await montar([], 'VENCIDO');

    expect(accion()?.dataset['permitidoEnSoloLectura']).toBeDefined();
    expect(accion()?.disabled).toBe(false);
  });

  it('con otro plan no muestra ese aviso', async () => {
    const { contenedor } = await montar([]);

    expect(contenedor.textContent).not.toContain('Tu prueba terminó');
  });

  it('si no se puede leer el plan, la pantalla funciona igual', async () => {
    const { accion, contenedor } = await montar([], 'ACTIVO', { 'GET /api/v1/consultorio': [problema(500, 'ERROR_INTERNO')] });

    expect(accion()?.disabled).toBe(false);
    expect(contenedor.textContent).not.toContain('Tu prueba terminó');
  });
});

describe('Exportar: descarga', () => {
  it('muestra el progreso, guarda con el nombre del servidor y confirma', async () => {
    let responder: (r: Response) => void = () => undefined;
    const pendiente = () => new Promise<Response>((resolver) => { responder = resolver; });
    const { accion, region } = await montar([pendiente]);

    accion()?.click();
    await esperar();
    expect(accion()?.disabled).toBe(true);
    expect(accion()?.textContent).toContain('Preparando tu archivo…');
    expect(region()?.textContent).toContain('Preparando tu archivo…');

    responder(zip()());
    for (let i = 0; i < 5; i += 1) await esperar();

    expect(guardados).toEqual([NOMBRE]);
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:ficticio');
    expect(region()?.textContent).toContain('Listo, descargamos tu archivo');
    expect(region()?.textContent).toContain(NOMBRE);
    expect(accion()?.disabled).toBe(false);
    expect(accion()?.textContent).toContain('Exportar mis datos');
  });

  it('un doble toque hace un solo pedido', async () => {
    const { accion, llamadas } = await montar([zip(), zip()]);

    accion()?.click();
    accion()?.click();
    for (let i = 0; i < 5; i += 1) await esperar();

    expect(llamadas.filter((l) => l.metodo === 'POST')).toHaveLength(1);
  });
});

describe('Exportar: errores', () => {
  const reciente = (segundos: string) => () =>
    new Response(JSON.stringify({ status: 429, codigo: 'EXPORTACION_RECIENTE' }), {
      status: 429,
      headers: { 'Content-Type': 'application/problem+json', 'Retry-After': segundos },
    });

  it('429: indica los minutos que faltan y vuelve a habilitar el botón', async () => {
    const { accion, region } = await montar([reciente('180')]);

    await exportar(accion());

    expect(region()?.textContent).toContain('Ya generaste una exportación hace poco');
    expect(region()?.textContent).toContain('Vas a poder pedir otra en 3 minutos.');
    expect(accion()?.disabled).toBe(false);
    expect(guardados).toEqual([]);
  });

  it('429: redondea hacia arriba y usa el singular', async () => {
    const { accion, region } = await montar([reciente('30')]);

    await exportar(accion());

    expect(region()?.textContent).toContain('Vas a poder pedir otra en 1 minuto.');
  });

  it('sin conexión: avisa y deja reintentar', async () => {
    const sinRed = () => Promise.reject(new TypeError('Failed to fetch'));
    const { accion, region } = await montar([sinRed, zip()]);

    await exportar(accion());
    expect(region()?.textContent).toContain('No tenés conexión');
    expect(accion()?.disabled).toBe(false);

    await exportar(accion());
    expect(guardados).toEqual([NOMBRE]);
  });

  it('corte a mitad de la descarga: no guarda nada', async () => {
    const cortado = () =>
      new Response(new ReadableStream<Uint8Array>({ pull: (control) => control.error(new Error('corte')) }), {
        status: 200,
        headers: { 'Content-Type': 'application/zip' },
      });
    const { accion, region } = await montar([cortado]);

    await exportar(accion());

    expect(region()?.textContent).toContain('No pudimos completar la exportación');
    expect(region()?.textContent).toContain('Probá de nuevo.');
    expect(guardados).toEqual([]);
    expect(accion()?.disabled).toBe(false);
  });

  it('un ZIP sin su cierre cuenta como corte y no se guarda', async () => {
    const { accion, region } = await montar([zip(new Uint8Array([0x50, 0x4b, 0x03, 0x04, 1, 2, 3]))]);

    await exportar(accion());

    expect(region()?.textContent).toContain('No pudimos completar la exportación');
    expect(guardados).toEqual([]);
  });
});

describe('Exportar: accesibilidad', () => {
  it('el botón cumple el objetivo táctil y describe el aviso de datos sensibles', async () => {
    const { accion, contenedor } = await montar([]);
    const ayuda = contenedor.querySelector(`#${accion()?.getAttribute('aria-describedby') ?? 'sin-id'}`);

    expect(accion()?.className).toContain('min-h-touch');
    expect(ayuda?.textContent).toContain('guardalo en un lugar seguro');
  });

  it('el resultado se anuncia en una región de estado que ya existe antes de exportar', async () => {
    const { accion, region } = await montar([zip()]);
    const antes = region();

    expect(antes?.getAttribute('role')).toBe('status');
    await exportar(accion());
    expect(region()).toBe(antes);
    expect(antes?.querySelector('[role="alert"]')).toBeNull();
  });

  it('conserva el foco en el botón al terminar', async () => {
    const { accion } = await montar([zip()]);
    accion()?.focus();

    await exportar(accion());

    expect(document.activeElement).toBe(accion());
  });
});
