import { describe, expect, it, vi } from 'vitest';
import { crearClienteHttp } from '../../shared/http';
import {
  activarPrestacion,
  crearPrestacion,
  desactivarPrestacion,
  editarPrestacion,
  guardarConfiguracion,
  guardarGrilla,
  listarPrestaciones,
  obtenerConfiguracion,
  obtenerGrilla,
} from './api';

// Tarea 1.3 de C-09: los endpoints de C-08 sobre un doble de fetch. Datos ficticios.

interface Llamada {
  readonly url: string;
  readonly metodo: string | undefined;
  readonly cuerpo: unknown;
  readonly autorizacion: string | null;
}

function cliente(respuesta: () => Response) {
  const llamadas: Llamada[] = [];
  const fetch = vi.fn(async (entrada: RequestInfo | URL, init: RequestInit = {}) => {
    llamadas.push({
      url: String(entrada),
      metodo: init.method,
      cuerpo: typeof init.body === 'string' ? (JSON.parse(init.body) as unknown) : undefined,
      autorizacion: new Headers(init.headers).get('Authorization'),
    });
    return respuesta();
  });
  const http = crearClienteHttp({ fetch });
  http.establecerToken('token-ficticio');
  return { http, llamadas };
}

const json = (cuerpo: unknown) => () => new Response(JSON.stringify(cuerpo), { headers: { 'Content-Type': 'application/json' } });

describe('api de configuración', () => {
  it('consultorio: GET y PUT con la sesión', async () => {
    const { http, llamadas } = cliente(json({ nombre: 'Consultorio Demo Sur' }));
    const solicitud = {
      nombre: 'Consultorio Demo Sur',
      slug: 'dra-ejemplo',
      telefonoContacto: '+5491100000001',
      zonaHoraria: 'America/Argentina/Buenos_Aires',
      anticipacionCancelacionMin: 1440,
      anticipacionReservaMin: 120,
      horizonteReservaDias: 60,
      pasoHuecosMin: 15,
      reservaPublicaActiva: true,
      version: 0,
    };

    await expect(obtenerConfiguracion(http)).resolves.toEqual({ nombre: 'Consultorio Demo Sur' });
    await guardarConfiguracion(http, solicitud);

    expect(llamadas.map((l) => `${l.metodo ?? ''} ${l.url}`)).toEqual(['GET /api/v1/consultorio', 'PUT /api/v1/consultorio']);
    expect(llamadas[1]?.cuerpo).toEqual(solicitud);
    expect(llamadas.every((l) => l.autorizacion === 'Bearer token-ficticio')).toBe(true);
  });

  it('prestaciones: listar activas o también inactivas', async () => {
    const { http, llamadas } = cliente(json([]));

    await listarPrestaciones(http);
    await listarPrestaciones(http, { incluirInactivas: true });

    expect(llamadas.map((l) => l.url)).toEqual(['/api/v1/prestaciones', '/api/v1/prestaciones?incluirInactivas=true']);
  });

  it('prestaciones: crear, editar, desactivar y activar', async () => {
    const { http, llamadas } = cliente(json({ id: 'p-1' }));
    const id = '7c1f4c2e-0000-4000-8000-000000000001';

    await crearPrestacion(http, { nombre: 'Limpieza', duracionMin: 45, visiblePublica: true });
    await editarPrestacion(http, id, { nombre: 'Limpieza', duracionMin: 60, visiblePublica: true, orden: 2 });
    await desactivarPrestacion(http, id);
    await activarPrestacion(http, id);

    expect(llamadas.map((l) => `${l.metodo ?? ''} ${l.url}`)).toEqual([
      'POST /api/v1/prestaciones',
      `PUT /api/v1/prestaciones/${id}`,
      `POST /api/v1/prestaciones/${id}/desactivar`,
      `POST /api/v1/prestaciones/${id}/activar`,
    ]);
    expect(llamadas[0]?.cuerpo).toEqual({ nombre: 'Limpieza', duracionMin: 45, visiblePublica: true });
  });

  it('el id de la prestación va codificado en la ruta', async () => {
    const { http, llamadas } = cliente(json({}));

    await desactivarPrestacion(http, '../consultorio');

    expect(llamadas[0]?.url).toBe('/api/v1/prestaciones/..%2Fconsultorio/desactivar');
  });

  it('disponibilidad: lee las franjas y reemplaza la semana completa', async () => {
    const franjas = [{ diaSemana: 1, horaInicio: '09:00', horaFin: '13:00' }];
    const { http, llamadas } = cliente(json({ franjas }));

    await expect(obtenerGrilla(http)).resolves.toEqual(franjas);
    await guardarGrilla(http, franjas);

    expect(llamadas.map((l) => `${l.metodo ?? ''} ${l.url}`)).toEqual(['GET /api/v1/disponibilidad', 'PUT /api/v1/disponibilidad']);
    expect(llamadas[1]?.cuerpo).toEqual({ franjas });
  });

  it('una grilla sin franjas en la respuesta se lee como semana vacía', async () => {
    const { http } = cliente(json({}));

    await expect(obtenerGrilla(http)).resolves.toEqual([]);
  });
});
