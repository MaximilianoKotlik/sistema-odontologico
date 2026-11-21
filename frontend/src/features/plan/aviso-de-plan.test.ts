import { afterEach, describe, expect, it, vi } from 'vitest';
import { CONTACTO_ACTIVACION, crearAvisoDePlan, ID_AVISO_SOLO_LECTURA } from './aviso-de-plan';
import { crearEstadoDelPlan, type EstadoDelPlan } from './estado-del-plan';

const ZONA = 'America/Argentina/Buenos_Aires';

function texto(e: Element | null): string {
  if (e === null) return '';
  const copia = e.cloneNode(true) as Element;
  copia.querySelectorAll('[aria-hidden="true"]').forEach((n) => n.remove());
  return (copia.textContent ?? '').replace(/\s+/g, ' ').trim();
}

async function conEstado(respuesta: unknown): Promise<{ estado: EstadoDelPlan; aviso: ReturnType<typeof crearAvisoDePlan> }> {
  const estado = crearEstadoDelPlan({ get: vi.fn(async () => respuesta) });
  const aviso = crearAvisoDePlan({ estado, zonaHoraria: () => ZONA });
  document.body.replaceChildren(aviso.elemento);
  await estado.cargar();
  montados.push(aviso);
  return { estado, aviso };
}

const montados: { destruir(): void }[] = [];
afterEach(() => montados.splice(0).forEach((a) => a.destruir()));

const prueba = (dias: number, pruebaHasta = '2026-12-09') => ({ plan: 'PRUEBA', pruebaHasta, diasDePruebaRestantes: dias, soloLectura: false });
const vencido = { plan: 'VENCIDO', pruebaHasta: '2026-11-20', diasDePruebaRestantes: null, soloLectura: true };

describe('aviso de plan (C-20, tarea 5.2; spec aviso-de-plan)', () => {
  it('durante la prueba muestra un aviso discreto con los días y la fecha', async () => {
    const { aviso } = await conEstado(prueba(20));

    const nodo = aviso.elemento.querySelector('[data-aviso-plan]');
    expect(texto(nodo)).toContain('Te quedan 20 días, hasta el miércoles 9 de diciembre');
    expect(nodo?.getAttribute('data-tono')).toBe('info');
    expect(nodo?.querySelector('a[href="/precios"]')).not.toBeNull();
  });

  it('con 7 días o menos el aviso se destaca', async () => {
    const { aviso } = await conEstado(prueba(5, '2026-11-24'));

    const nodo = aviso.elemento.querySelector('[data-aviso-plan]');
    expect(texto(nodo)).toContain('Te quedan 5 días de prueba');
    expect(texto(nodo)).toContain('Tu prueba termina el martes 24 de noviembre');
    expect(texto(nodo)).toContain('no se borra nada');
    expect(nodo?.getAttribute('data-tono')).toBe('advertencia');
  });

  it('el último día dice que la prueba termina hoy', async () => {
    const { aviso } = await conEstado(prueba(1, '2026-11-21'));

    expect(texto(aviso.elemento)).toContain('Tu prueba termina hoy');
    expect(texto(aviso.elemento)).not.toContain('Te quedan');
  });

  it('con el plan activo no muestra nada', async () => {
    const { aviso } = await conEstado({ plan: 'ACTIVO', pruebaHasta: null, diasDePruebaRestantes: null, soloLectura: false });

    expect(aviso.elemento.hidden).toBe(true);
    expect(aviso.elemento.querySelector('[data-aviso-plan]')).toBeNull();
  });

  it('con el plan vencido explica el solo lectura, ofrece exportar y el contacto', async () => {
    const { aviso } = await conEstado(vencido);

    const nodo = aviso.elemento.querySelector(`#${ID_AVISO_SOLO_LECTURA}`);
    expect(texto(nodo)).toContain('Tu prueba terminó: la agenda está en solo lectura');
    expect(texto(nodo)).toContain('No se borra nada');
    expect(nodo?.getAttribute('data-tono')).toBe('advertencia');
    expect(nodo?.querySelector('a[href="/app/exportar"]')?.textContent).toContain('Exportar mis datos');
    expect(nodo?.querySelector(`a[href="mailto:${CONTACTO_ACTIVACION}"]`)).not.toBeNull();
  });

  it('se anuncia como status, sin robar el foco, y las acciones miden 44 px', async () => {
    const enfocado = document.activeElement;
    const { aviso } = await conEstado(vencido);

    expect(aviso.elemento.getAttribute('role')).toBe('status');
    expect(document.activeElement).toBe(enfocado);
    for (const accion of aviso.elemento.querySelectorAll('[data-aviso-plan] .actions a, [data-aviso-plan] [data-accion]')) {
      expect(accion.className).toContain('min-h-touch');
    }
  });

  it('el error de una escritura rechazada es un alert de peligro arriba del aviso', async () => {
    const { aviso } = await conEstado(vencido);

    aviso.mostrarEscrituraRechazada();

    const error = aviso.elemento.querySelector('[role="alert"]');
    expect(texto(error)).toContain('No se guardó el cambio');
    expect(texto(error)).toContain('No se perdió nada');
    expect(error?.getAttribute('data-tono')).toBe('peligro');
    expect(aviso.elemento.firstElementChild).toBe(error);
  });

  it('si la prueba vence con la app abierta pasa a solo lectura sin recargar', async () => {
    const respuestas: unknown[] = [prueba(1, '2026-11-21'), vencido];
    const estado = crearEstadoDelPlan({ get: vi.fn(async () => respuestas.shift()) });
    const aviso = crearAvisoDePlan({ estado, zonaHoraria: () => ZONA });
    montados.push(aviso);
    await estado.cargar();

    await estado.cargar();

    expect(aviso.elemento.querySelector(`#${ID_AVISO_SOLO_LECTURA}`)).not.toBeNull();
  });
});
