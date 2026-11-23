import { describe, expect, it } from 'vitest';
import { esCampoConsultorio } from './consultorio';

// Issue #60: los errores traen el nombre del campo como texto; se buscan con un type guard en vez de un doble cast.

describe('esCampoConsultorio', () => {
  it('reconoce cada campo del formulario del consultorio', () => {
    const campos = ['nombre', 'slug', 'direccion', 'telefonoContacto', 'anticipacionCancelacion', 'anticipacionReserva', 'horizonteReservaDias', 'pasoHuecosMin', 'zonaHoraria'];
    expect(campos.filter((campo) => !esCampoConsultorio(campo))).toEqual([]);
  });

  it('rechaza nombres desconocidos, incluidos los heredados de Object', () => {
    expect(['campoQueNoExiste', 'duracionMin', 'constructor', 'toString', '__proto__', 'hasOwnProperty', ''].filter(esCampoConsultorio)).toEqual([]);
  });
});
