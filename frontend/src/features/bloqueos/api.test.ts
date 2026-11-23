import { describe, expect, it } from 'vitest';
import { ApiError } from '../../shared/http';
import { crearContextoDePagina, json, problema } from '../configuracion/prueba-contexto';
import { crearBloqueo, eliminarBloqueo, listarBloqueos } from './api';

// Tarea 1.2 de C-14 (design D8): cliente de bloqueos de C-12 sobre un doble de fetch. Datos ficticios.

describe('listarBloqueos', () => {
  it('pide los vigentes y descarta entradas incompletas', async () => {
    const { contexto, llamadas } = crearContextoDePagina({
      'GET /api/v1/bloqueos': [json([{ id: 'b-1', inicio: '2026-11-20T18:00:00Z', fin: '2026-11-20T19:00:00Z', motivo: 'Reunión' }, { id: 'b-2', inicio: '2026-11-26T12:00:00Z', fin: '2026-11-26T13:30:00Z' }, { id: 'roto' }])],
    });
    expect(await listarBloqueos(contexto.http)).toEqual([
      { id: 'b-1', inicio: '2026-11-20T18:00:00Z', fin: '2026-11-20T19:00:00Z', motivo: 'Reunión' },
      { id: 'b-2', inicio: '2026-11-26T12:00:00Z', fin: '2026-11-26T13:30:00Z', motivo: null },
    ]);
    expect(llamadas[0]?.metodo).toBe('GET');
  });
});

describe('crearBloqueo', () => {
  it('manda inicio, fin y el motivo recortado solo si lo hay', async () => {
    const respuesta = { id: 'b-3', inicio: '2026-11-20T18:00:00Z', fin: '2026-11-20T22:00:00Z', motivo: 'Curso' };
    const { contexto, llamadas } = crearContextoDePagina({ 'POST /api/v1/bloqueos': [json(respuesta, 201), json({ ...respuesta, motivo: null }, 201)] });
    expect((await crearBloqueo(contexto.http, { inicio: respuesta.inicio, fin: respuesta.fin, motivo: ' Curso ' })).motivo).toBe('Curso');
    await crearBloqueo(contexto.http, { inicio: respuesta.inicio, fin: respuesta.fin, motivo: '' });
    expect(llamadas.map((l) => l.cuerpo)).toEqual([
      { inicio: respuesta.inicio, fin: respuesta.fin, motivo: 'Curso' },
      { inicio: respuesta.inicio, fin: respuesta.fin },
    ]);
  });

  it('el 409 BLOQUEO_CON_TURNOS trae la lista de turnos en conflicto', async () => {
    const { contexto } = crearContextoDePagina({ 'POST /api/v1/bloqueos': [problema(409, 'BLOQUEO_CON_TURNOS', { turnosEnConflicto: ['t-1', 't-2'] })] });
    const error = await crearBloqueo(contexto.http, { inicio: '2026-11-20T18:00:00Z', fin: '2026-11-20T22:00:00Z', motivo: '' }).catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).datos['turnosEnConflicto']).toEqual(['t-1', 't-2']);
  });
});

describe('eliminarBloqueo', () => {
  it('usa DELETE con el id', async () => {
    const { contexto, llamadas } = crearContextoDePagina({ 'DELETE /api/v1/bloqueos/b-1': [() => new Response(null, { status: 204 })] });
    await eliminarBloqueo(contexto.http, 'b-1');
    expect(llamadas[0]?.metodo).toBe('DELETE');
  });
});
