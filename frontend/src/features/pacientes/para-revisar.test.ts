import { describe, expect, it, vi } from 'vitest';
import { crearContextoDePagina, json, problema } from '../configuracion/prueba-contexto';
import { crearParaRevisar } from './para-revisar';

// C-17, tarea 4.1 (design D3): la cantidad de pacientes para revisar, compartida por la navegación y la agenda.

const RUTA = 'GET /api/v1/pacientes/listado?requiereRevision=true&page=0&size=1';

describe('crearParaRevisar', () => {
  it('arranca sin dato, lo pide y avisa a los suscriptores', async () => {
    const { contexto } = crearContextoDePagina({ [RUTA]: [json({ items: [], total: 3 }), json({ items: [], total: 2 })] });
    const paraRevisar = crearParaRevisar(contexto.http);
    const visto = vi.fn();
    paraRevisar.suscribir(visto);
    expect(paraRevisar.cantidad()).toBeNull();

    await paraRevisar.actualizar();
    expect(paraRevisar.cantidad()).toBe(3);
    await paraRevisar.actualizar();

    expect(visto.mock.calls.map((c) => c[0])).toEqual([3, 2]);
  });

  it('si falla el pedido conserva lo que tenía', async () => {
    const { contexto } = crearContextoDePagina({ [RUTA]: [json({ items: [], total: 4 }), problema(500, 'ERROR_INTERNO')] });
    const paraRevisar = crearParaRevisar(contexto.http);
    await paraRevisar.actualizar();
    await paraRevisar.actualizar();
    expect(paraRevisar.cantidad()).toBe(4);
  });

  it('olvidar borra el dato y avisa cero; desuscribirse deja de avisar', async () => {
    const { contexto } = crearContextoDePagina({ [RUTA]: [json({ items: [], total: 1 }), json({ items: [], total: 5 })] });
    const paraRevisar = crearParaRevisar(contexto.http);
    const visto = vi.fn();
    const dejar = paraRevisar.suscribir(visto);
    await paraRevisar.actualizar();
    paraRevisar.olvidar();
    expect(paraRevisar.cantidad()).toBeNull();
    dejar();
    await paraRevisar.actualizar();
    expect(visto.mock.calls.map((c) => c[0])).toEqual([1, 0]);
  });
});
