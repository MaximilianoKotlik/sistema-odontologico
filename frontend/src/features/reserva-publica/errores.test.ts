import { describe, expect, it } from 'vitest';
import { ApiError } from '../../shared/http';
import { traducirErrorPublico } from './errores';

const error = (status: number, codigo: string, datos: Record<string, unknown> = {}, retryAfter: string | null = null) =>
  new ApiError(status, codigo, 'detalle para desarrolladores', datos, retryAfter);

describe('traducirErrorPublico (design D8)', () => {
  it('HUECO_NO_DISPONIBLE trae los huecos actualizados del día, solo los que son texto', () => {
    expect(traducirErrorPublico(error(409, 'HUECO_NO_DISPONIBLE', { huecos: ['2026-11-24T12:00:00Z', 3, '2026-11-24T12:15:00Z'] }))).toEqual({
      tipo: 'hueco-ocupado',
      huecos: ['2026-11-24T12:00:00Z', '2026-11-24T12:15:00Z'],
    });
    expect(traducirErrorPublico(error(409, 'HUECO_NO_DISPONIBLE'))).toEqual({ tipo: 'hueco-ocupado', huecos: [] });
  });

  it('LIMITE_TURNOS no expone nada más que el código', () => {
    expect(traducirErrorPublico(error(409, 'LIMITE_TURNOS', { turnos: ['no debería aparecer'] }))).toEqual({ tipo: 'limite-turnos' });
  });

  it('RESERVA_NO_DISPONIBLE y FUERA_DE_PLAZO llevan el teléfono del consultorio si viene', () => {
    expect(traducirErrorPublico(error(409, 'RESERVA_NO_DISPONIBLE', { telefonoContacto: '+5491100000001' }))).toEqual({
      tipo: 'reserva-no-disponible',
      telefono: '+5491100000001',
    });
    expect(traducirErrorPublico(error(409, 'FUERA_DE_PLAZO', { telefonoContacto: '+5491100000001' }))).toEqual({
      tipo: 'fuera-de-plazo',
      telefono: '+5491100000001',
    });
    expect(traducirErrorPublico(error(409, 'FUERA_DE_PLAZO'))).toEqual({ tipo: 'fuera-de-plazo', telefono: null });
  });

  it('CONFLICTO_CONCURRENCIA pide recargar', () => {
    expect(traducirErrorPublico(error(409, 'CONFLICTO_CONCURRENCIA'))).toEqual({ tipo: 'conflicto' });
  });

  it('cualquier 404 es "no encontrado", sin distinguir la causa', () => {
    expect(traducirErrorPublico(error(404, 'RECURSO_NO_ENCONTRADO'))).toEqual({ tipo: 'no-encontrado' });
    expect(traducirErrorPublico(error(404, 'ERROR_INESPERADO'))).toEqual({ tipo: 'no-encontrado' });
  });

  it('DNI_INVALIDO, TELEFONO_INVALIDO y VALIDACION se traducen a errores por campo', () => {
    expect(traducirErrorPublico(error(400, 'DNI_INVALIDO'))).toEqual({ tipo: 'campos', campos: ['dni'] });
    expect(traducirErrorPublico(error(400, 'TELEFONO_INVALIDO'))).toEqual({ tipo: 'campos', campos: ['telefono'] });
    expect(
      traducirErrorPublico(error(400, 'VALIDACION', { errores: [{ campo: 'email', codigo: 'EMAIL' }, { campo: 'nombre' }, { otro: 1 }, 'x'] })),
    ).toEqual({ tipo: 'campos', campos: ['email', 'nombre'] });
  });

  it('429 dice cuántos segundos esperar según Retry-After, con un minuto por defecto', () => {
    expect(traducirErrorPublico(error(429, 'DEMASIADAS_SOLICITUDES', {}, '60'))).toEqual({ tipo: 'demasiadas', segundos: 60 });
    expect(traducirErrorPublico(error(429, 'DEMASIADAS_SOLICITUDES', {}, '120'))).toEqual({ tipo: 'demasiadas', segundos: 120 });
    expect(traducirErrorPublico(error(429, 'DEMASIADAS_SOLICITUDES'))).toEqual({ tipo: 'demasiadas', segundos: 60 });
    expect(traducirErrorPublico(error(429, 'DEMASIADAS_SOLICITUDES', {}, 'Wed, 21 Oct 2026 07:28:00 GMT'))).toEqual({ tipo: 'demasiadas', segundos: 60 });
  });

  it('sin red es "sin conexión" y el resto es un error reintentable', () => {
    expect(traducirErrorPublico(error(0, 'SIN_CONEXION'))).toEqual({ tipo: 'sin-conexion' });
    expect(traducirErrorPublico(error(500, 'ERROR_INTERNO'))).toEqual({ tipo: 'inesperado' });
    expect(traducirErrorPublico(new Error('cualquier cosa'))).toEqual({ tipo: 'inesperado' });
  });
});

describe('mensajeDeEspera', () => {
  it('redondea a minutos para el texto del 429', async () => {
    const { mensajeDeEspera } = await import('./errores');
    expect(mensajeDeEspera(60)).toBe('Esperá un minuto y volvé a intentar.');
    expect(mensajeDeEspera(30)).toBe('Esperá un minuto y volvé a intentar.');
    expect(mensajeDeEspera(150)).toBe('Esperá 3 minutos y volvé a intentar.');
  });
});
