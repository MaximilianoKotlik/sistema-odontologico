import { describe, expect, it } from 'vitest';
import { ApiError } from '../../shared/http';
import { crearContextoDePagina, json, problema } from '../configuracion/prueba-contexto';
import { cantidadParaRevisar, confirmarRevision, corregirPaciente, detallePaciente, listarPacientes } from './api';

// C-17, tareas 4.1 a 4.5 (design D3, D4 y D9): cliente de pacientes sobre un doble de fetch. Datos ficticios.

const ANA = {
  id: 'p-1',
  nombre: 'Ana',
  apellido: 'Ejemplo',
  dni: '90000001',
  telefono: '+5491100000001',
  email: null,
  requiereRevision: true,
  informado: {
    nombre: 'Anna',
    apellido: 'Ejemplo',
    email: null,
    informadoAt: '2026-11-18T13:00:00.123456Z',
    turno: { id: 't-1', inicio: '2026-11-23T13:30:00Z', prestacion: 'Consulta' },
  },
};
const BRUNO = { id: 'p-2', nombre: 'Bruno', apellido: 'Prueba', dni: '90000002', telefono: '+5491100000002', email: 'bruno@example.com', requiereRevision: false, informado: null };

describe('listarPacientes', () => {
  it('pide la página con el filtro y descarta entradas incompletas', async () => {
    const { contexto, llamadas } = crearContextoDePagina({
      'GET /api/v1/pacientes/listado?requiereRevision=true&page=1&size=20': [json({ items: [ANA, { id: 'roto' }], total: 21 })],
    });
    const pagina = await listarPacientes(contexto.http, { soloConRevision: true, pagina: 1 });
    expect(pagina.total).toBe(21);
    expect(pagina.items).toHaveLength(1);
    expect(pagina.items[0]?.informado?.nombre).toBe('Anna');
    expect(pagina.items[0]?.informado?.turno?.prestacion).toBe('Consulta');
    expect(llamadas[0]?.metodo).toBe('GET');
  });

  it('sin el filtro pide todos y un paciente sin revisión no trae lo informado', async () => {
    const { contexto } = crearContextoDePagina({
      'GET /api/v1/pacientes/listado?requiereRevision=false&page=0&size=20': [json({ items: [{ ...BRUNO, informado: { nombre: 'X' } }], total: 1 })],
    });
    const pagina = await listarPacientes(contexto.http, { soloConRevision: false, pagina: 0 });
    expect(pagina.items[0]).toEqual({ ...BRUNO, informado: null });
  });
});

describe('cantidadParaRevisar (D3)', () => {
  it('lee el total de la lista filtrada de a uno', async () => {
    const { contexto } = crearContextoDePagina({ 'GET /api/v1/pacientes/listado?requiereRevision=true&page=0&size=1': [json({ items: [ANA], total: 3 })] });
    expect(await cantidadParaRevisar(contexto.http)).toBe(3);
  });

  it('una respuesta sin total cuenta como cero', async () => {
    const { contexto } = crearContextoDePagina({ 'GET /api/v1/pacientes/listado?requiereRevision=true&page=0&size=1': [json({})] });
    expect(await cantidadParaRevisar(contexto.http)).toBe(0);
  });
});

describe('detallePaciente', () => {
  it('trae el paciente y un 404 llega como ApiError', async () => {
    const { contexto } = crearContextoDePagina({ 'GET /api/v1/pacientes/p-1': [json(ANA), problema(404, 'RECURSO_NO_ENCONTRADO')] });
    expect((await detallePaciente(contexto.http, 'p-1')).informado?.informadoAt).toBe('2026-11-18T13:00:00.123456Z');
    const error = await detallePaciente(contexto.http, 'p-1').catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).codigo).toBe('RECURSO_NO_ENCONTRADO');
  });
});

describe('confirmarRevision y corregirPaciente (D9)', () => {
  it('mandan el informadoAt que se vio', async () => {
    const revisada = { ...ANA, requiereRevision: false, informado: null };
    const { contexto, llamadas } = crearContextoDePagina({
      'POST /api/v1/pacientes/p-1/confirmar-revision': [json(revisada)],
      'PATCH /api/v1/pacientes/p-1': [json({ ...revisada, nombre: 'Anna' })],
    });
    expect((await confirmarRevision(contexto.http, 'p-1', ANA.informado.informadoAt)).requiereRevision).toBe(false);
    const corregida = await corregirPaciente(contexto.http, 'p-1', ANA.informado.informadoAt, { nombre: ' Anna ', apellido: 'Ejemplo', email: ' ' });
    expect(corregida.nombre).toBe('Anna');
    expect(llamadas.map((l) => l.cuerpo)).toEqual([
      { informadoAt: '2026-11-18T13:00:00.123456Z' },
      { informadoAt: '2026-11-18T13:00:00.123456Z', nombre: 'Anna', apellido: 'Ejemplo' },
    ]);
  });
});
