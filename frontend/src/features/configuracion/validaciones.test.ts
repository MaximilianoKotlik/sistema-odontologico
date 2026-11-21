import { describe, expect, it } from 'vitest';
import type { FormularioConsultorio } from './unidades';
import { normalizarSlug, validarConsultorio, validarDuracion, validarGrilla, validarSlug, type FranjaEditable } from './validaciones';

// Tareas 2.2 y 2.3 de C-09 (design D4, D4b, D5, D6, D7). Textos de los mockups aprobados (#23). Datos ficticios.

describe('normalizarSlug (D5: mientras se escribe)', () => {
  it('pasa a minúsculas, cambia espacios por guiones y saca tildes', () => {
    expect(normalizarSlug('Dra Ana Pérez')).toBe('dra-ana-perez');
    expect(normalizarSlug('Ñandú')).toBe('nandu');
  });

  it('descarta lo que no es letra, número o guion', () => {
    expect(normalizarSlug('dra.ejemplo_2!')).toBe('draejemplo2');
  });

  it('deja el guion final mientras se escribe la palabra siguiente', () => {
    expect(normalizarSlug('dra ')).toBe('dra-');
  });
});

describe('validarSlug (RN-TE-03)', () => {
  it('acepta slugs válidos', () => {
    for (const slug of ['dra-ejemplo', 'abc', 'consultorio-2', 'a'.repeat(40)]) {
      expect(validarSlug(slug), slug).toBeNull();
    }
  });

  it('rechaza largo, mayúsculas, caracteres y guiones fuera de lugar', () => {
    for (const slug of ['', 'ab', 'a'.repeat(41), 'Dra-ejemplo', 'dra_ejemplo', '-dra', 'dra-', 'dra--ejemplo']) {
      expect(validarSlug(slug), slug).not.toBeNull();
    }
  });

  it('no conoce las palabras reservadas: las valida el backend (D5)', () => {
    expect(validarSlug('admin')).toBeNull();
  });
});

describe('validarDuracion (D6)', () => {
  it('acepta de 5 a 480 en múltiplos de 5', () => {
    for (const minutos of [5, 30, 45, 480]) expect(validarDuracion(minutos), String(minutos)).toBeNull();
  });

  it('pide múltiplos de 5 con el texto del mockup', () => {
    expect(validarDuracion(47)).toBe('Usá múltiplos de 5 minutos (por ejemplo 45 o 50).');
  });

  it('rechaza fuera de rango o sin valor', () => {
    for (const minutos of [0, 4, 485, Number.NaN]) expect(validarDuracion(minutos), String(minutos)).not.toBeNull();
  });
});

const valido: FormularioConsultorio = {
  nombre: 'Consultorio Demo Sur',
  slug: 'dra-ejemplo',
  direccion: '',
  telefonoContacto: '+54 9 11 0000-0001',
  zonaHoraria: 'America/Argentina/Buenos_Aires',
  anticipacionCancelacion: { valor: 24, unidad: 'horas' },
  anticipacionReserva: { valor: 2, unidad: 'horas' },
  horizonteReservaDias: 60,
  pasoHuecosMin: 15,
  reservaPublicaActiva: true,
  version: 0,
};

const campos = (formulario: FormularioConsultorio) => validarConsultorio(formulario).map((e) => e.campo);
const mensajeDe = (formulario: FormularioConsultorio, campo: string) => validarConsultorio(formulario).find((e) => e.campo === campo)?.mensaje;

describe('validarConsultorio', () => {
  it('no marca nada con datos válidos', () => {
    expect(validarConsultorio(valido)).toEqual([]);
  });

  it('nombre obligatorio y de hasta 120 caracteres', () => {
    expect(mensajeDe({ ...valido, nombre: '  ' }, 'nombre')).toBe('Poné el nombre del consultorio.');
    expect(campos({ ...valido, nombre: 'a'.repeat(121) })).toEqual(['nombre']);
    expect(campos({ ...valido, nombre: 'a'.repeat(120) })).toEqual([]);
  });

  it('teléfono obligatorio (D4b)', () => {
    expect(mensajeDe({ ...valido, telefonoContacto: ' ' }, 'telefonoContacto')).toBe(
      'Poné un teléfono de contacto: es lo que ven tus pacientes cuando no pueden cancelar por el link o si pausás la reserva.',
    );
  });

  it('slug con el formato de RN-TE-03', () => {
    expect(campos({ ...valido, slug: 'dra-' })).toEqual(['slug']);
  });

  it('anticipación de cancelación de 0 a 168 horas', () => {
    expect(campos({ ...valido, anticipacionCancelacion: { valor: 168, unidad: 'horas' } })).toEqual([]);
    expect(campos({ ...valido, anticipacionCancelacion: { valor: 169, unidad: 'horas' } })).toEqual(['anticipacionCancelacion']);
    expect(campos({ ...valido, anticipacionCancelacion: { valor: -1, unidad: 'horas' } })).toEqual(['anticipacionCancelacion']);
  });

  it('horizonte de 1 a 365 días', () => {
    expect(mensajeDe({ ...valido, horizonteReservaDias: 400 }, 'horizonteReservaDias')).toBe('El horizonte va de 1 a 365 días.');
    expect(campos({ ...valido, horizonteReservaDias: 0 })).toEqual(['horizonteReservaDias']);
    expect(campos({ ...valido, horizonteReservaDias: 365 })).toEqual([]);
  });

  it('anticipación de reserva menor que el horizonte, con el mensaje en su campo', () => {
    expect(mensajeDe({ ...valido, anticipacionReserva: { valor: 1500, unidad: 'horas' } }, 'anticipacionReserva')).toBe(
      'Tiene que ser menor que el horizonte (60 días = 1440 horas). Si no, no queda ningún horario para reservar.',
    );
    expect(campos({ ...valido, anticipacionReserva: { valor: 1440, unidad: 'horas' } })).toEqual(['anticipacionReserva']);
    expect(campos({ ...valido, anticipacionReserva: { valor: 1439, unidad: 'horas' } })).toEqual([]);
  });

  it('la anticipación de reserva en minutos también se compara con el horizonte', () => {
    expect(campos({ ...valido, horizonteReservaDias: 1, anticipacionReserva: { valor: 1440, unidad: 'minutos' } })).toEqual(['anticipacionReserva']);
    expect(campos({ ...valido, horizonteReservaDias: 1, anticipacionReserva: { valor: 90, unidad: 'minutos' } })).toEqual([]);
  });

  it('paso de huecos solo entre las opciones permitidas', () => {
    expect(campos({ ...valido, pasoHuecosMin: 25 })).toEqual(['pasoHuecosMin']);
  });

  it('devuelve los errores en el orden del formulario, para el resumen', () => {
    expect(campos({ ...valido, nombre: '', slug: 'x', telefonoContacto: '', horizonteReservaDias: 400 })).toEqual([
      'nombre',
      'slug',
      'telefonoContacto',
      'horizonteReservaDias',
    ]);
  });
});

const franja = (diaSemana: number, horaInicio: string, horaFin: string): FranjaEditable => ({ diaSemana, horaInicio, horaFin });

describe('validarGrilla (D7: intervalos semiabiertos)', () => {
  it('acepta franjas contiguas y días distintos', () => {
    expect(validarGrilla([franja(1, '09:00', '13:00'), franja(1, '13:00', '15:00'), franja(2, '09:00', '13:00')])).toEqual([]);
  });

  it('acepta una semana vacía (la confirmación es de la pantalla)', () => {
    expect(validarGrilla([])).toEqual([]);
  });

  it('marca en su día las franjas solapadas, con el texto del mockup', () => {
    expect(validarGrilla([franja(1, '09:00', '13:00'), franja(1, '12:00', '15:00')])).toEqual([
      { diaSemana: 1, mensaje: 'Las franjas 09:00–13:00 y 12:00–15:00 se superponen. Cambiá una de las dos.' },
    ]);
  });

  it('detecta el solapamiento aunque las franjas estén desordenadas', () => {
    expect(validarGrilla([franja(3, '12:00', '15:00'), franja(3, '09:00', '13:00')]).map((e) => e.diaSemana)).toEqual([3]);
  });

  it('marca la franja que termina antes de empezar, con su número en el día', () => {
    expect(validarGrilla([franja(5, '09:00', '13:00'), franja(5, '18:00', '15:00')])).toEqual([
      { diaSemana: 5, mensaje: 'La franja 2 termina antes de empezar. Poné una hora de fin posterior al inicio.' },
    ]);
    expect(validarGrilla([franja(5, '10:00', '10:00')]).map((e) => e.diaSemana)).toEqual([5]);
  });

  it('pide horas en múltiplos de 5 minutos y franjas completas', () => {
    expect(validarGrilla([franja(2, '09:03', '13:00')]).map((e) => e.diaSemana)).toEqual([2]);
    expect(validarGrilla([franja(2, '', '13:00')]).map((e) => e.diaSemana)).toEqual([2]);
  });
});
