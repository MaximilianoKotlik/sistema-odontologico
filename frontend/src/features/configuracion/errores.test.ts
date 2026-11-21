import { describe, expect, it } from 'vitest';
import { ApiError } from '../../shared/http';
import { traducirError } from './errores';

// Tarea 2.4 de C-09 (design D8): cada código de C-08 va al campo, la prestación o el día que corresponde.

const error = (codigo: string, datos: Record<string, unknown> = {}, status = 400) => new ApiError(status, codigo, '', datos);
const campoDe = (codigo: string, datos: Record<string, unknown> = {}) => traducirError(error(codigo, datos)).campos.map((c) => c.campo);

describe('traducirError', () => {
  it('SLUG_EN_USO: en el slug, con las sugerencias de la respuesta', () => {
    const traducido = traducirError(error('SLUG_EN_USO', { sugerencias: ['dra-ejemplo-2', 'dra-ejemplo-3'] }, 409));

    expect(traducido.campos.map((c) => c.campo)).toEqual(['slug']);
    expect(traducido.sugerencias).toEqual(['dra-ejemplo-2', 'dra-ejemplo-3']);
  });

  it('SLUG_EN_USO sin sugerencias válidas no inventa ninguna', () => {
    expect(traducirError(error('SLUG_EN_USO', { sugerencias: [1, null] }, 409)).sugerencias).toEqual([]);
    expect(traducirError(error('SLUG_EN_USO', {}, 409)).sugerencias).toEqual([]);
  });

  it('SLUG_RESERVADO y SLUG_INVALIDO: en el slug, con el motivo', () => {
    const reservado = traducirError(error('SLUG_RESERVADO')).campos[0];
    expect(reservado?.campo).toBe('slug');
    expect(reservado?.mensaje).toContain('reservada');
    expect(campoDe('SLUG_INVALIDO')).toEqual(['slug']);
  });

  it('TELEFONO_INVALIDO: en el teléfono, con el ejemplo del mockup', () => {
    expect(traducirError(error('TELEFONO_INVALIDO')).campos).toEqual([
      { campo: 'telefonoContacto', mensaje: 'Este teléfono no es válido. Probá con el código de área, por ejemplo 11 0000-0001.' },
    ]);
  });

  it('ZONA_HORARIA_INVALIDA y ANTICIPACION_FUERA_DE_HORIZONTE: en su campo', () => {
    expect(campoDe('ZONA_HORARIA_INVALIDA')).toEqual(['zonaHoraria']);
    expect(campoDe('ANTICIPACION_FUERA_DE_HORIZONTE')).toEqual(['anticipacionReserva']);
  });

  it('VALIDACION: un error por campo informado, conservando el nombre del contrato', () => {
    const traducido = traducirError(
      error('VALIDACION', {
        errores: [
          { campo: 'telefonoContacto', codigo: 'OBLIGATORIO' },
          { campo: 'nombre', codigo: 'TAMANO' },
        ],
      }),
    );

    expect(traducido.campos.map((c) => c.campo)).toEqual(['telefonoContacto', 'nombre']);
    expect(traducido.campos[0]?.mensaje).toContain('teléfono de contacto');
  });

  it('VALIDACION sin detalle usable queda como aviso general', () => {
    const traducido = traducirError(error('VALIDACION', { errores: 'roto' }));
    expect(traducido.campos).toEqual([]);
    expect(traducido.aviso).not.toBeNull();
  });

  it('prestaciones: duración y nombre repetido en el diálogo', () => {
    expect(campoDe('DURACION_INVALIDA')).toEqual(['duracionMin']);
    expect(traducirError(error('PRESTACION_DUPLICADA', {}, 409)).campos).toEqual([{ campo: 'nombre', mensaje: 'Ya tenés una prestación con ese nombre.' }]);
  });

  it('FRANJAS_SOLAPADAS: marca los días que informa la respuesta', () => {
    const traducido = traducirError(error('FRANJAS_SOLAPADAS', { dias: [3, 5] }));

    expect(traducido.dias).toEqual([
      { diaSemana: 3, mensaje: 'Encontramos franjas superpuestas en este día. Revisalas y volvé a guardar.' },
      { diaSemana: 5, mensaje: 'Encontramos franjas superpuestas en este día. Revisalas y volvé a guardar.' },
    ]);
  });

  it('FRANJAS_SOLAPADAS sin días (carrera en la base) queda como aviso general', () => {
    const traducido = traducirError(error('FRANJAS_SOLAPADAS'));
    expect(traducido.dias).toEqual([]);
    expect(traducido.aviso).not.toBeNull();
  });

  it('FRANJA_INVALIDA: aviso general arriba del editor', () => {
    const traducido = traducirError(error('FRANJA_INVALIDA'));
    expect(traducido.campos).toEqual([]);
    expect(traducido.aviso).not.toBeNull();
  });

  it('CONFLICTO_CONCURRENCIA: marca el conflicto para ofrecer los valores actuales', () => {
    const traducido = traducirError(error('CONFLICTO_CONCURRENCIA', {}, 409));
    expect(traducido.conflicto).toBe(true);
    expect(traducido.aviso).not.toBeNull();
  });

  it('red caída o un código desconocido: aviso recuperable, sin mostrar el código', () => {
    for (const codigo of ['SIN_CONEXION', 'ERROR_INESPERADO', 'ALGO_NUEVO']) {
      const traducido = traducirError(error(codigo, {}, 0));
      expect(traducido.recuperable, codigo).toBe(true);
      expect(traducido.aviso, codigo).not.toContain(codigo);
    }
  });
});
