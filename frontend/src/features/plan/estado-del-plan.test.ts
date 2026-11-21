import { describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../shared/http';
import { crearEstadoDelPlan, esEstadoPlan, RUTA_PLAN } from './estado-del-plan';

const prueba = (dias: number) => ({ plan: 'PRUEBA', pruebaHasta: '2026-12-09', diasDePruebaRestantes: dias, soloLectura: false });
const vencido = { plan: 'VENCIDO', pruebaHasta: '2026-11-20', diasDePruebaRestantes: null, soloLectura: true };

describe('estado del plan (C-20, tarea 5.1)', () => {
  it('pide GET /api/v1/plan y guarda los días que da el backend', async () => {
    const get = vi.fn(async () => prueba(5));
    const estado = crearEstadoDelPlan({ get });

    await estado.cargar();

    expect(get).toHaveBeenCalledWith(RUTA_PLAN);
    expect(estado.get()).toEqual(prueba(5));
    expect(estado.soloLectura()).toBe(false);
  });

  it('con el plan vencido queda en solo lectura y avisa a los suscriptores', async () => {
    const estado = crearEstadoDelPlan({ get: vi.fn(async () => vencido) });
    const cambio = vi.fn();
    estado.suscribir(cambio);

    await estado.cargar();

    expect(estado.soloLectura()).toBe(true);
    expect(cambio).toHaveBeenCalledTimes(1);
  });

  it('si la consulta falla no inventa un estado ni bloquea la app', async () => {
    const estado = crearEstadoDelPlan({ get: vi.fn(async () => Promise.reject(new ApiError(0, 'SIN_CONEXION', ''))) });

    await estado.cargar();

    expect(estado.get()).toBeNull();
    expect(estado.soloLectura()).toBe(false);
  });

  it('descarta una respuesta con otra forma', async () => {
    const estado = crearEstadoDelPlan({ get: vi.fn(async () => ({ plan: 'GRATIS' })) });

    await estado.cargar();

    expect(estado.get()).toBeNull();
  });

  it('olvidar borra el estado al cerrar sesión', async () => {
    const estado = crearEstadoDelPlan({ get: vi.fn(async () => vencido) });
    await estado.cargar();

    estado.olvidar();

    expect(estado.get()).toBeNull();
    expect(estado.soloLectura()).toBe(false);
  });

  it('valida la forma de la respuesta', () => {
    expect(esEstadoPlan(prueba(3))).toBe(true);
    expect(esEstadoPlan(vencido)).toBe(true);
    expect(esEstadoPlan({ ...vencido, soloLectura: 'si' })).toBe(false);
    expect(esEstadoPlan({ ...prueba(3), diasDePruebaRestantes: '3' })).toBe(false);
    expect(esEstadoPlan(null)).toBe(false);
  });
});
