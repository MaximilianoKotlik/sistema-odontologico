import { describe, expect, it } from 'vitest';
import {
  LARGO_MOTIVO_CANCELACION,
  LARGO_NOTA,
  ajustarDuracion,
  estadoDelContador,
  finDerivado,
  normalizarDni,
  validarCelular,
  validarDni,
  validarDuracion,
  validarHora,
  validarMotivoCancelacion,
  validarNota,
  validarPacienteNuevo,
} from './validaciones';

// Tarea 2.1 de C-14 (design D3, D4, D7; RN-AG-08, RN-AG-12, DD-24, DD-32). Datos ficticios.
const BA = 'America/Argentina/Buenos_Aires';

describe('finDerivado', () => {
  it('es inicio más la duración, en la zona del consultorio', () => {
    expect(finDerivado('2026-11-20', '12:00', 30, BA)).toEqual({ instante: '2026-11-20T15:30:00.000Z', hora: '12:30' });
  });

  it('cruza la medianoche sin romperse', () => {
    expect(finDerivado('2026-11-20', '23:40', 45, BA).hora).toBe('00:25');
  });
});

describe('validarHora', () => {
  it('pide una hora', () => expect(validarHora('')).toBe('Elegí la hora de inicio.'));
  it('acepta múltiplos de 5', () => expect(validarHora('10:15')).toBeNull());
  it('rechaza minutos que no son múltiplo de 5', () => expect(validarHora('10:12')).toBe('Elegí una hora en pasos de 5 minutos, por ejemplo 10:10 o 10:15.'));
});

describe('validarDuracion y ajustarDuracion', () => {
  it.each([5, 30, 480])('%i min es válida', (minutos) => expect(validarDuracion(minutos)).toBeNull());
  it.each([0, 3, 485, 32])('%i min no es válida', (minutos) => expect(validarDuracion(minutos)).not.toBeNull());

  it('suma y resta de a 5 sin pasarse de 5 ni de 480', () => {
    expect(ajustarDuracion(30, 5)).toBe(35);
    expect(ajustarDuracion(5, -5)).toBe(5);
    expect(ajustarDuracion(480, 5)).toBe(480);
  });
});

describe('DNI', () => {
  it('saca puntos y espacios', () => expect(normalizarDni(' 30.000.003 ')).toBe('30000003'));
  it('acepta 7 u 8 números', () => {
    expect(validarDni('3000001')).toBeNull();
    expect(validarDni('30.000.003')).toBeNull();
  });
  it('rechaza 5 dígitos con el mensaje del spec', () => expect(validarDni('30000')).toBe('El DNI tiene 7 u 8 números, sin puntos.'));
  it('rechaza letras', () => expect(validarDni('30A00003')).toBe('El DNI tiene 7 u 8 números, sin puntos.'));
  it('es obligatorio (DD-24)', () => expect(validarDni('')).toBe('Escribí el DNI del paciente.'));
});

describe('validarCelular', () => {
  it('es obligatorio (DD-32)', () => expect(validarCelular(' ')).toBe('Escribí el celular del paciente.'));
  it('acepta un número con código de área', () => expect(validarCelular('11 0000-0009')).toBeNull());
  it('acepta el formato internacional', () => expect(validarCelular('+54 9 11 0000-0009')).toBeNull());
  it('rechaza un número demasiado corto', () => expect(validarCelular('0000')).toBe('Revisá el celular: poné el código de área, por ejemplo 11 0000-0001.'));
});

describe('validarPacienteNuevo', () => {
  const valido = { nombre: 'Iris', apellido: 'Ejemplo', dni: '30000009', telefono: '11 0000-0009', email: '' };

  it('sin errores con los datos completos y el email vacío', () => expect(validarPacienteNuevo(valido)).toEqual([]));

  it('marca cada campo que falta', () => {
    expect(validarPacienteNuevo({ nombre: '', apellido: ' ', dni: '30000', telefono: '', email: 'no-es-email' }).map((e) => e.campo)).toEqual(['nombre', 'apellido', 'dni', 'telefono', 'email']);
  });

  it('limita nombre y apellido a 80 caracteres', () => {
    expect(validarPacienteNuevo({ ...valido, nombre: 'a'.repeat(81) })[0]?.mensaje).toBe('Puede tener hasta 80 caracteres.');
  });
});

describe('nota y motivo', () => {
  it('la nota admite hasta 500 caracteres', () => {
    expect(LARGO_NOTA).toBe(500);
    expect(validarNota('a'.repeat(500))).toBeNull();
    expect(validarNota('a'.repeat(501))).toBe('1 carácter de más');
    expect(validarNota('a'.repeat(510))).toBe('10 caracteres de más');
  });

  it('el motivo de cancelación admite hasta 200', () => {
    expect(LARGO_MOTIVO_CANCELACION).toBe(200);
    expect(validarMotivoCancelacion('a'.repeat(201))).toBe('El motivo puede tener hasta 200 caracteres.');
    expect(validarMotivoCancelacion('')).toBeNull();
  });
});

describe('estadoDelContador (D9)', () => {
  it('muestra "N / 500" y se pasa al superar el límite', () => {
    expect(estadoDelContador(12, 500)).toEqual({ texto: '12 / 500', excedido: false, anunciar: null });
    expect(estadoDelContador(501, 500)).toEqual({ texto: '501 / 500', excedido: true, anunciar: '1 carácter de más' });
  });

  it('se anuncia cada 50 caracteres y no en cada tecla', () => {
    expect(estadoDelContador(50, 500).anunciar).toBe('Quedan 450 caracteres');
    expect(estadoDelContador(51, 500).anunciar).toBeNull();
  });
});
