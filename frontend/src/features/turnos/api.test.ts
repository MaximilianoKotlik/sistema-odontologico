import { describe, expect, it } from 'vitest';
import { crearContextoDePagina, json, problema } from '../configuracion/prueba-contexto';
import { ApiError } from '../../shared/http';
import {
  ajustarDuracion,
  buscarPacientes,
  cambiarEstado,
  crearTurno,
  editarNota,
  obtenerPasoHuecos,
  obtenerPrestacionesActivas,
  reprogramarTurno,
  turnosFueraDeDisponibilidad,
} from './api';

// Tarea 1.2 de C-14 (design D1): cliente tipado de las escrituras de C-12 sobre un doble de fetch. Datos ficticios.

const DETALLE = {
  id: 't-1',
  inicio: '2026-11-20T15:00:00Z',
  fin: '2026-11-20T15:30:00Z',
  duracionMin: 30,
  estado: 'RESERVADO',
  esSobreturno: false,
  origen: 'MANUAL',
  prestacion: { id: 'p-1', nombre: 'Consulta' },
  paciente: { id: 'pa-1', nombre: 'Lucía', apellido: 'Muestra', dni: '30000010', telefono: '+5491100000010', email: null, requiereRevision: false },
  tieneNota: false,
  version: 4,
};

describe('buscarPacientes', () => {
  it('pide ?q= codificado y descarta entradas incompletas', async () => {
    const { contexto, llamadas } = crearContextoDePagina({
      'GET /api/v1/pacientes?q=30.000': [json([{ id: 'pa-1', nombre: 'Carla', apellido: 'Ficticia', dni: '30000003', telefono: '+5491100000003', requiereRevision: false }, { nombre: 'Sin id' }])],
    });
    const encontrados = await buscarPacientes(contexto.http, '30.000');
    expect(llamadas[0]?.url).toBe('/api/v1/pacientes?q=30.000');
    expect(encontrados).toEqual([{ id: 'pa-1', nombre: 'Carla', apellido: 'Ficticia', dni: '30000003', telefono: '+5491100000003' }]);
  });
});

describe('crearTurno', () => {
  it('con un paciente existente manda su id, la prestación, el inicio y el sobreturno', async () => {
    const { contexto, llamadas } = crearContextoDePagina({ 'POST /api/v1/turnos': [json({ turno: DETALLE, pacienteReutilizado: false }, 201)] });
    const creado = await crearTurno(contexto.http, { paciente: { tipo: 'existente', id: 'pa-1' }, prestacionId: 'p-1', inicio: '2026-11-20T15:00:00.000Z', esSobreturno: false });
    expect(llamadas[0]?.cuerpo).toEqual({ pacienteId: 'pa-1', prestacionId: 'p-1', inicio: '2026-11-20T15:00:00.000Z', esSobreturno: false });
    expect(creado.turno.id).toBe('t-1');
    expect(creado.turno.version).toBe(4);
    expect(creado.pacienteReutilizado).toBe(false);
  });

  it('con un paciente nuevo manda sus datos recortados y el email solo si lo hay', async () => {
    const { contexto, llamadas } = crearContextoDePagina({ 'POST /api/v1/turnos': [json({ turno: DETALLE, pacienteReutilizado: true }, 201)] });
    const creado = await crearTurno(contexto.http, {
      paciente: { tipo: 'nuevo', datos: { nombre: ' Iris ', apellido: 'Ejemplo', dni: '30.000.009', telefono: '11 0000-0009', email: ' ' } },
      prestacionId: 'p-1',
      inicio: '2026-11-20T15:00:00.000Z',
      esSobreturno: true,
    });
    expect(llamadas[0]?.cuerpo).toEqual({
      pacienteNuevo: { nombre: 'Iris', apellido: 'Ejemplo', dni: '30000009', telefono: '11 0000-0009' },
      prestacionId: 'p-1',
      inicio: '2026-11-20T15:00:00.000Z',
      esSobreturno: true,
    });
    expect(creado.pacienteReutilizado).toBe(true);
  });

  it('propaga el 409 con sus datos para que el formulario lo explique', async () => {
    const { contexto } = crearContextoDePagina({ 'POST /api/v1/turnos': [problema(409, 'TURNO_SOLAPADO', { turnoEnConflicto: { id: 't-9', inicio: '2026-11-20T13:30:00Z' } })] });
    const error = await crearTurno(contexto.http, { paciente: { tipo: 'existente', id: 'pa-1' }, prestacionId: 'p-1', inicio: '2026-11-20T15:00:00.000Z', esSobreturno: false }).catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).datos['turnoEnConflicto']).toEqual({ id: 't-9', inicio: '2026-11-20T13:30:00Z' });
  });
});

describe('acciones sobre el turno', () => {
  it('cambiarEstado manda la acción, la versión y el motivo solo al cancelar', async () => {
    const { contexto, llamadas } = crearContextoDePagina({ 'PATCH /api/v1/turnos/t-1/estado': [json({ ...DETALLE, estado: 'CONFIRMADO' }), json({ ...DETALLE, estado: 'CANCELADO' })] });
    expect((await cambiarEstado(contexto.http, 't-1', { accion: 'CONFIRMAR', version: 4 })).estado).toBe('CONFIRMADO');
    await cambiarEstado(contexto.http, 't-1', { accion: 'CANCELAR', version: 5, motivo: ' Lo pidió por teléfono ' });
    expect(llamadas.map((l) => l.cuerpo)).toEqual([
      { accion: 'CONFIRMAR', version: 4 },
      { accion: 'CANCELAR', version: 5, motivo: 'Lo pidió por teléfono' },
    ]);
  });

  it('reprogramar, duración y nota usan su ruta con la versión leída', async () => {
    const { contexto, llamadas } = crearContextoDePagina({
      'POST /api/v1/turnos/t-1/reprogramar': [json(DETALLE)],
      'PATCH /api/v1/turnos/t-1/duracion': [json(DETALLE)],
      'PATCH /api/v1/turnos/t-1/nota': [json(DETALLE), json(DETALLE)],
    });
    await reprogramarTurno(contexto.http, 't-1', { inicio: '2026-11-20T19:30:00.000Z', version: 4 });
    await ajustarDuracion(contexto.http, 't-1', { duracionMin: 45, version: 4 });
    await editarNota(contexto.http, 't-1', { nota: 'Trae radiografía', version: 4 });
    await editarNota(contexto.http, 't-1', { nota: '   ', version: 5 });
    expect(llamadas.map((l) => [l.metodo, l.cuerpo])).toEqual([
      ['POST', { inicio: '2026-11-20T19:30:00.000Z', version: 4 }],
      ['PATCH', { duracionMin: 45, version: 4 }],
      ['PATCH', { nota: 'Trae radiografía', version: 4 }],
      ['PATCH', { nota: null, version: 5 }],
    ]);
  });
});

describe('lecturas de apoyo', () => {
  it('turnosFueraDeDisponibilidad mapea al modelo de la agenda', async () => {
    const { contexto } = crearContextoDePagina({ 'GET /api/v1/turnos/fuera-de-disponibilidad': [json([DETALLE, { id: 'roto' }])] });
    const turnos = await turnosFueraDeDisponibilidad(contexto.http);
    expect(turnos.map((t) => [t.id, t.paciente.nombre])).toEqual([['t-1', 'Lucía']]);
  });

  it('obtenerPrestacionesActivas deja solo las activas con id, nombre y duración', async () => {
    const { contexto } = crearContextoDePagina({
      'GET /api/v1/prestaciones': [json([{ id: 'p-1', nombre: 'Consulta', duracionMin: 30, activa: true }, { id: 'p-2', nombre: 'Vieja', duracionMin: 20, activa: false }, { nombre: 'Rota' }])],
    });
    expect(await obtenerPrestacionesActivas(contexto.http)).toEqual([{ id: 'p-1', nombre: 'Consulta', duracionMin: 30 }]);
  });

  it('obtenerPasoHuecos lee el paso del consultorio y usa 15 si no se puede', async () => {
    const { contexto } = crearContextoDePagina({ 'GET /api/v1/consultorio': [json({ pasoHuecosMin: 20 }), problema(500, 'ERROR_INTERNO')] });
    expect(await obtenerPasoHuecos(contexto.http)).toBe(20);
    expect(await obtenerPasoHuecos(contexto.http)).toBe(15);
  });
});
