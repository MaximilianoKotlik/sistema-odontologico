import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, crearClienteHttp } from '../../shared/http';
import { marcarVisto, obtenerAgendaDeSemana, obtenerFranjas, obtenerTurno, olvidarFranjas } from './api';

// Tarea 1.2 de C-13 (design D2): cliente tipado de la agenda sobre un doble de fetch. Datos ficticios.
const BA = 'America/Argentina/Buenos_Aires';

interface Llamada {
  readonly url: string;
  readonly metodo: string | undefined;
  readonly cuerpo: unknown;
}

function cliente(...respuestas: (() => Response | Promise<Response>)[]) {
  const llamadas: Llamada[] = [];
  const fetch = vi.fn(async (entrada: RequestInfo | URL, init: RequestInit = {}) => {
    llamadas.push({ url: String(entrada), metodo: init.method, cuerpo: typeof init.body === 'string' ? (JSON.parse(init.body) as unknown) : undefined });
    const siguiente = respuestas.shift();
    if (siguiente === undefined) throw new Error('Sin respuesta preparada');
    return siguiente();
  });
  const http = crearClienteHttp({ fetch });
  http.establecerToken('token-ficticio');
  return { http, llamadas };
}

const json = (cuerpo: unknown, status = 200) => () => new Response(JSON.stringify(cuerpo), { status, headers: { 'Content-Type': 'application/json' } });
const sinRed = () => Promise.reject(new TypeError('Failed to fetch'));

const TURNO_API = {
  id: 't-1',
  inicio: '2026-11-20T13:30:00Z',
  fin: '2026-11-20T14:30:00Z',
  duracionMin: 60,
  estado: 'CONFIRMADO',
  esSobreturno: false,
  origen: 'MANUAL',
  prestacion: { id: 'p-1', nombre: 'Extracción' },
  paciente: { id: 'pa-1', nombre: 'Carla', apellido: 'Ficticia' },
  tieneNota: true,
  version: 3,
};

beforeEach(() => olvidarFranjas());

describe('obtenerAgendaDeSemana', () => {
  it('pide de lunes a lunes en la zona del consultorio, con instantes UTC', async () => {
    const { http, llamadas } = cliente(json({ turnos: [], bloqueos: [] }));
    await obtenerAgendaDeSemana(http, '2026-11-16', BA);
    expect(llamadas[0]?.url).toBe(`/api/v1/agenda?desde=${encodeURIComponent('2026-11-16T03:00:00.000Z')}&hasta=${encodeURIComponent('2026-11-23T03:00:00.000Z')}`);
  });

  it('mapea turnos y bloqueos al modelo de la vista', async () => {
    const { http } = cliente(
      json({
        turnos: [TURNO_API, { ...TURNO_API, id: 't-2', estado: 'CANCELADO', canceladoPor: 'PACIENTE', cambioPaciente: 'CANCELO', novedadPacienteAt: '2026-11-20T11:12:00Z', tieneNota: false }],
        bloqueos: [{ id: 'b-1', inicio: '2026-11-20T18:00:00Z', fin: '2026-11-20T19:00:00Z', motivo: 'Reunión' }, { id: 'b-2', inicio: '2026-11-21T12:00:00Z', fin: '2026-11-21T13:00:00Z' }],
      }),
    );
    const agenda = await obtenerAgendaDeSemana(http, '2026-11-16', BA);
    expect(agenda.turnos[0]).toEqual({
      id: 't-1',
      inicio: '2026-11-20T13:30:00Z',
      fin: '2026-11-20T14:30:00Z',
      duracionMin: 60,
      estado: 'CONFIRMADO',
      esSobreturno: false,
      origen: 'MANUAL',
      prestacion: 'Extracción',
      paciente: { nombre: 'Carla', apellido: 'Ficticia' },
      tieneNota: true,
      canceladoPor: null,
      cambioPaciente: null,
      novedadPacienteAt: null,
    });
    expect(agenda.turnos[1]).toMatchObject({ estado: 'CANCELADO', canceladoPor: 'PACIENTE', cambioPaciente: 'CANCELO', novedadPacienteAt: '2026-11-20T11:12:00Z' });
    expect(agenda.bloqueos).toEqual([
      { id: 'b-1', inicio: '2026-11-20T18:00:00Z', fin: '2026-11-20T19:00:00Z', motivo: 'Reunión' },
      { id: 'b-2', inicio: '2026-11-21T12:00:00Z', fin: '2026-11-21T13:00:00Z', motivo: null },
    ]);
  });

  it('descarta entradas que no traen lo mínimo para dibujarse', async () => {
    const { http } = cliente(json({ turnos: [TURNO_API, { id: 't-x', estado: 'CONFIRMADO' }, { ...TURNO_API, id: 't-y', estado: 'RARO' }], bloqueos: [{ id: 'b-x' }] }));
    const agenda = await obtenerAgendaDeSemana(http, '2026-11-16', BA);
    expect(agenda.turnos.map((t) => t.id)).toEqual(['t-1']);
    expect(agenda.bloqueos).toEqual([]);
  });

  it('una respuesta vacía o sin listas es una agenda vacía', async () => {
    const { http } = cliente(json({}));
    expect(await obtenerAgendaDeSemana(http, '2026-11-16', BA)).toEqual({ turnos: [], bloqueos: [] });
  });

  it('sin red y sin agenda guardada por el Service Worker, falla con SIN_CONEXION', async () => {
    const { http } = cliente(sinRed);
    await expect(obtenerAgendaDeSemana(http, '2026-11-16', BA)).rejects.toMatchObject({ codigo: 'SIN_CONEXION' });
  });

  it('sin red pero con la agenda guardada (la responde el Service Worker), la muestra igual', async () => {
    // El Service Worker (NetworkFirst sobre /api/v1/agenda) contesta con la copia guardada: para la página es un 200.
    const guardada = () => new Response(JSON.stringify({ turnos: [TURNO_API], bloqueos: [] }), { headers: { 'Content-Type': 'application/json', Date: 'Fri, 20 Nov 2026 13:32:00 GMT' } });
    const { http } = cliente(guardada);
    expect((await obtenerAgendaDeSemana(http, '2026-11-16', BA)).turnos).toHaveLength(1);
  });

  it('propaga los errores del servidor', async () => {
    const { http } = cliente(json({ status: 500, codigo: 'ERROR_INTERNO' }, 500));
    await expect(obtenerAgendaDeSemana(http, '2026-11-16', BA)).rejects.toBeInstanceOf(ApiError);
  });
});

describe('obtenerFranjas', () => {
  it('lee la disponibilidad y la guarda en memoria durante la sesión', async () => {
    const { http, llamadas } = cliente(json({ franjas: [{ diaSemana: 1, horaInicio: '09:00', horaFin: '13:00' }, { diaSemana: 2 }] }));
    expect(await obtenerFranjas(http)).toEqual([{ diaSemana: 1, horaInicio: '09:00', horaFin: '13:00' }]);
    expect(await obtenerFranjas(http)).toEqual([{ diaSemana: 1, horaInicio: '09:00', horaFin: '13:00' }]);
    expect(llamadas).toHaveLength(1);
  });

  it('sin red y sin franjas en memoria, devuelve null (la agenda se muestra sin huecos libres)', async () => {
    const { http } = cliente(sinRed);
    expect(await obtenerFranjas(http)).toBeNull();
  });

  it('olvidarFranjas obliga a volver a pedirlas (al cerrar sesión o cambiar los horarios)', async () => {
    const { http, llamadas } = cliente(json({ franjas: [] }), json({ franjas: [] }));
    await obtenerFranjas(http);
    olvidarFranjas();
    await obtenerFranjas(http);
    expect(llamadas).toHaveLength(2);
  });
});

describe('obtenerTurno', () => {
  it('pide el detalle y lo mapea con los datos del paciente', async () => {
    const { http, llamadas } = cliente(
      json({
        ...TURNO_API,
        paciente: { id: 'pa-1', nombre: 'Carla', apellido: 'Ficticia', dni: '30000003', telefono: '+5491100000003', email: 'carla.ficticia@example.com' },
        notaInterna: 'Trae radiografía',
        cambioPaciente: 'REPROGRAMO',
        novedadPacienteAt: '2026-11-20T00:40:00Z',
        horarioAnterior: { inicio: '2026-11-19T19:30:00Z', fin: '2026-11-19T20:00:00Z' },
      }),
    );
    const detalle = await obtenerTurno(http, 't 1');
    expect(llamadas[0]?.url).toBe('/api/v1/turnos/t%201');
    expect(detalle).toMatchObject({
      id: 't-1',
      prestacion: 'Extracción',
      paciente: { nombre: 'Carla', apellido: 'Ficticia', dni: '30000003', telefono: '+5491100000003', email: 'carla.ficticia@example.com' },
      notaInterna: 'Trae radiografía',
      motivoCancelacion: null,
      cambioPaciente: 'REPROGRAMO',
      horarioAnterior: { inicio: '2026-11-19T19:30:00Z', fin: '2026-11-19T20:00:00Z' },
      version: 3,
    });
  });

  it('sin email ni nota ni horario anterior, los deja en null', async () => {
    const { http } = cliente(json({ ...TURNO_API, paciente: { nombre: 'Ana', apellido: 'Ejemplo', dni: '30000001', telefono: '+5491100000001' } }));
    expect(await obtenerTurno(http, 't-1')).toMatchObject({ paciente: { email: null }, notaInterna: null, horarioAnterior: null });
  });

  it('un 404 se propaga para mostrar "No encontramos este turno"', async () => {
    const { http } = cliente(json({ status: 404, codigo: 'TURNO_NO_ENCONTRADO' }, 404));
    await expect(obtenerTurno(http, 't-1')).rejects.toMatchObject({ status: 404 });
  });

  it('una respuesta sin lo mínimo cuenta como error inesperado', async () => {
    const { http } = cliente(json({ id: 't-1' }));
    await expect(obtenerTurno(http, 't-1')).rejects.toMatchObject({ codigo: 'ERROR_INESPERADO' });
  });
});

describe('marcarVisto', () => {
  it('manda el novedadPacienteAt que se vio (D2, DD-30)', async () => {
    const { http, llamadas } = cliente(() => new Response(null, { status: 204 }));
    await marcarVisto(http, 't-1', '2026-11-20T00:40:00Z');
    expect(llamadas[0]).toEqual({ url: '/api/v1/turnos/t-1/visto', metodo: 'POST', cuerpo: { novedadAt: '2026-11-20T00:40:00Z' } });
  });
});
