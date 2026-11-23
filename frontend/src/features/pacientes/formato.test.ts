import { describe, expect, it } from 'vitest';
import { cantidadConDatos, lineaDeLoInformado, nombreDeLista } from './formato';

// C-17, tarea 4.2: textos de la lista de pacientes del mockup aprobado (#36). Datos ficticios.

const ZONA = 'America/Argentina/Buenos_Aires';
const AHORA = new Date('2026-11-22T15:00:00Z');

describe('lineaDeLoInformado', () => {
  it('nombra lo escrito con comillas y el día de la reserva en la zona del consultorio', () => {
    expect(lineaDeLoInformado({ nombre: 'Anna', apellido: 'Ejemplo', informadoAt: '2026-11-18T13:00:00Z' }, ZONA, AHORA)).toBe('Escribió «Anna Ejemplo» al reservar el 18 de noviembre');
  });

  it('una reserva de hoy dice "hoy", aunque en UTC ya sea otro día', () => {
    expect(lineaDeLoInformado({ nombre: 'Karla', apellido: 'Ficticia', informadoAt: '2026-11-22T02:30:00Z' }, ZONA, new Date('2026-11-22T23:00:00Z'))).toBe(
      'Escribió «Karla Ficticia» al reservar el 21 de noviembre',
    );
    expect(lineaDeLoInformado({ nombre: 'Karla', apellido: 'Ficticia', informadoAt: '2026-11-22T12:00:00Z' }, ZONA, AHORA)).toBe('Escribió «Karla Ficticia» al reservar hoy');
  });
});

describe('nombreDeLista y cantidadConDatos', () => {
  it('apellido primero, como el orden de la lista', () => {
    expect(nombreDeLista({ nombre: 'Ana', apellido: 'Ejemplo' })).toBe('Ejemplo, Ana');
  });

  it('singular y plural', () => {
    expect(cantidadConDatos(1)).toBe('1 paciente con datos para revisar');
    expect(cantidadConDatos(3)).toBe('3 pacientes con datos para revisar');
  });
});
