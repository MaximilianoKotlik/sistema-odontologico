import { describe, expect, it } from 'vitest';
import { botonConTexto, crearContextoDePagina, enviar, escribir, esperar, json, problema, type Guion } from '../features/configuracion/prueba-contexto';
import { pagina } from './configuracion-prestaciones';

// Tarea 3.3 de C-09 (spec app-configuracion/prestaciones; mockup `prestaciones.html`). Datos ficticios.

const LISTA = '/api/v1/prestaciones?incluirInactivas=true';
const consulta = { id: 'p-consulta', nombre: 'Consulta', duracionMin: 30, activa: true, visiblePublica: true, orden: 1 };
const control = { id: 'p-control', nombre: 'Control', duracionMin: 20, activa: true, visiblePublica: false, orden: 2 };
const blanqueamiento = { id: 'p-blanq', nombre: 'Blanqueamiento', duracionMin: 60, activa: false, visiblePublica: true, orden: 3 };

async function montar(guion: Guion) {
  const prueba = crearContextoDePagina(guion);
  pagina.montar(prueba.contexto);
  await esperar();
  const activas = () => [...prueba.contenedor.querySelectorAll('[data-prestaciones-activas] li')].map((li) => li.querySelector('[data-nombre]')?.textContent);
  const inactivas = () => [...prueba.contenedor.querySelectorAll('[data-prestaciones-inactivas] li')].map((li) => li.querySelector('[data-nombre]')?.textContent);
  const dialogo = () => document.querySelector<HTMLElement>('[role="dialog"], [role="alertdialog"]');
  const anuncio = () => prueba.contenedor.querySelector('[aria-live="polite"]')?.textContent ?? '';
  return { ...prueba, activas, inactivas, dialogo, anuncio };
}

const porEtiqueta = (raiz: ParentNode, etiqueta: string) => raiz.querySelector<HTMLButtonElement>(`button[aria-label="${etiqueta}"]`);

describe('Configuración: prestaciones', () => {
  it('lista las activas en orden, con duración y visibilidad en texto e ícono', async () => {
    const { contenedor, activas } = await montar({ [`GET ${LISTA}`]: [json([control, consulta, blanqueamiento])] });

    expect(activas()).toEqual(['Consulta', 'Control']);
    const filaControl = [...contenedor.querySelectorAll('[data-prestaciones-activas] li')][1];
    expect(filaControl?.textContent).toContain('20 min');
    expect(filaControl?.textContent).toContain('Solo para vos');
    expect(filaControl?.querySelector('svg')).not.toBeNull();
    expect(contenedor.querySelector('nav [aria-current="page"]')?.textContent).toBe('Prestaciones');
  });

  it('sin prestaciones activas muestra el estado vacío con "Agregar prestación"', async () => {
    const { contenedor } = await montar({ [`GET ${LISTA}`]: [json([])] });

    expect(contenedor.textContent).toContain('Todavía no cargaste prestaciones');
    expect(contenedor.textContent).toContain('Sin prestaciones no se pueden dar turnos');
    expect(botonConTexto(contenedor, 'Agregar prestación')).not.toBeNull();
  });

  it('agrega una prestación desde el diálogo, la suma al final y lo anuncia', async () => {
    const limpieza = { id: 'p-limpieza', nombre: 'Limpieza', duracionMin: 45, activa: true, visiblePublica: true, orden: 3 };
    const { contenedor, llamadas, activas, dialogo, anuncio } = await montar({
      [`GET ${LISTA}`]: [json([consulta, control])],
      'POST /api/v1/prestaciones': [json(limpieza, 201)],
    });

    botonConTexto(contenedor, 'Agregar')?.click();
    const caja = dialogo();
    expect(caja?.querySelector('h2')?.textContent).toBe('Agregar prestación');
    escribir(caja?.querySelector<HTMLInputElement>('[name="nombre"]') ?? null, 'Limpieza');
    botonConTexto(caja ?? document, '45')?.click();
    enviar(caja ?? document.body);
    await esperar();

    expect(llamadas.find((l) => l.metodo === 'POST')?.cuerpo).toEqual({ nombre: 'Limpieza', duracionMin: 45, visiblePublica: true, orden: 3 });
    expect(dialogo()).toBeNull();
    expect(activas()).toEqual(['Consulta', 'Control', 'Limpieza']);
    expect(anuncio()).toContain('Agregamos Limpieza');
  });

  it('una duración que no es múltiplo de 5 se marca y no se envía', async () => {
    const { contenedor, llamadas, dialogo } = await montar({ [`GET ${LISTA}`]: [json([consulta])] });
    botonConTexto(contenedor, 'Agregar')?.click();
    const caja = dialogo();
    escribir(caja?.querySelector<HTMLInputElement>('[name="nombre"]') ?? null, 'Limpieza');
    escribir(caja?.querySelector<HTMLInputElement>('[name="duracionMin"]') ?? null, '47');

    enviar(caja ?? document.body);
    await esperar();

    expect(llamadas.filter((l) => l.metodo === 'POST')).toHaveLength(0);
    expect(caja?.textContent).toContain('Usá múltiplos de 5 minutos (por ejemplo 45 o 50).');
    expect(caja?.querySelector('[name="duracionMin"]')?.getAttribute('aria-invalid')).toBe('true');
  });

  it('nombre repetido: el diálogo sigue abierto con el error en el nombre', async () => {
    const { contenedor, dialogo } = await montar({
      [`GET ${LISTA}`]: [json([consulta])],
      'POST /api/v1/prestaciones': [problema(409, 'PRESTACION_DUPLICADA')],
    });
    botonConTexto(contenedor, 'Agregar')?.click();
    const caja = dialogo();
    escribir(caja?.querySelector<HTMLInputElement>('[name="nombre"]') ?? null, 'CONSULTA');
    escribir(caja?.querySelector<HTMLInputElement>('[name="duracionMin"]') ?? null, '30');

    enviar(caja ?? document.body);
    await esperar();

    expect(dialogo()).not.toBeNull();
    expect(caja?.querySelector('[name="nombre"]')?.getAttribute('aria-invalid')).toBe('true');
    expect(caja?.textContent).toContain('Ya tenés una prestación con ese nombre.');
  });

  it('al editar la duración avisa que los turnos ya dados la conservan', async () => {
    const { contenedor, dialogo } = await montar({ [`GET ${LISTA}`]: [json([consulta])] });

    porEtiqueta(contenedor, 'Editar Consulta')?.click();
    const caja = dialogo();
    expect(caja?.querySelector('h2')?.textContent).toBe('Editar prestación');
    escribir(caja?.querySelector<HTMLInputElement>('[name="duracionMin"]') ?? null, '45');

    expect(caja?.textContent).toContain('Pasa de 30 a 45 minutos para los turnos nuevos');
  });

  it('"Subir" intercambia el orden con la anterior, lo guarda y lo anuncia', async () => {
    const { contenedor, llamadas, activas, anuncio } = await montar({
      [`GET ${LISTA}`]: [json([consulta, control])],
      'PUT /api/v1/prestaciones/p-control': [json({ ...control, orden: 1 })],
      'PUT /api/v1/prestaciones/p-consulta': [json({ ...consulta, orden: 2 })],
    });

    expect(porEtiqueta(contenedor, 'Subir Consulta')?.disabled).toBe(true);
    porEtiqueta(contenedor, 'Subir Control')?.click();
    await esperar();

    expect(activas()).toEqual(['Control', 'Consulta']);
    expect(llamadas.filter((l) => l.metodo === 'PUT').map((l) => [l.url, (l.cuerpo as { orden: number }).orden])).toEqual([
      ['/api/v1/prestaciones/p-control', 1],
      ['/api/v1/prestaciones/p-consulta', 2],
    ]);
    expect(anuncio()).toContain('Control pasó al lugar 1');
  });

  it('desactivar pide confirmación con RN-AG-10 y la pasa a Inactivas con "Activar"', async () => {
    const { contenedor, activas, inactivas, dialogo } = await montar({
      [`GET ${LISTA}`]: [json([consulta, control])],
      'POST /api/v1/prestaciones/p-control/desactivar': [json({ ...control, activa: false })],
    });

    porEtiqueta(contenedor, 'Desactivar Control')?.click();
    const confirmacion = dialogo();
    expect(confirmacion?.textContent).toContain('¿Desactivar Control?');
    expect(confirmacion?.textContent).toContain('Los turnos que ya tiene siguen vigentes.');
    botonConTexto(confirmacion ?? document, 'Desactivar')?.click();
    await esperar();

    expect(activas()).toEqual(['Consulta']);
    expect(inactivas()).toEqual(['Control']);
    expect(porEtiqueta(contenedor, 'Activar Control')).not.toBeNull();
  });

  it('desactivar la última activa avisa que sin prestaciones no se pueden dar turnos', async () => {
    const { contenedor, dialogo } = await montar({ [`GET ${LISTA}`]: [json([consulta])] });

    porEtiqueta(contenedor, 'Desactivar Consulta')?.click();

    expect(dialogo()?.textContent).toContain('Es tu única prestación activa.');
  });

  it('"Activar" la devuelve al final de las activas', async () => {
    const { contenedor, activas, inactivas } = await montar({
      [`GET ${LISTA}`]: [json([consulta, control, blanqueamiento])],
      'POST /api/v1/prestaciones/p-blanq/activar': [json({ ...blanqueamiento, activa: true })],
    });

    porEtiqueta(contenedor, 'Activar Blanqueamiento')?.click();
    await esperar();

    expect(activas()).toEqual(['Consulta', 'Control', 'Blanqueamiento']);
    expect(inactivas()).toEqual([]);
  });

  it('no ofrece borrar (RN-AG-07)', async () => {
    const { contenedor } = await montar({ [`GET ${LISTA}`]: [json([consulta, blanqueamiento])] });

    const textos = [...contenedor.querySelectorAll('button')].map((b) => `${b.textContent ?? ''} ${b.getAttribute('aria-label') ?? ''}`);
    expect(textos.some((t) => /borrar|eliminar/i.test(t))).toBe(false);
  });
});
