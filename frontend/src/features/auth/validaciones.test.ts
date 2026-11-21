import { describe, expect, it } from 'vitest';
import {
  bytesUtf8,
  cumpleRequisitos,
  esEmailValido,
  evaluarRequisitos,
  validarRegistro,
  validarRepeticion,
  type DatosRegistro,
} from './validaciones';

// Tarea 1.1 de C-07 (design D1). Datos ficticios evidentes (regla dura 17).
const EMAIL = 'odontologa@example.com';

describe('bytesUtf8', () => {
  it('cuenta un byte por letra sin tilde', () => {
    expect(bytesUtf8('abcdefghij')).toBe(10);
  });

  it('cuenta dos bytes por cada letra con tilde o ñ, como el backend', () => {
    expect(bytesUtf8('ñandú')).toBe(7);
    expect(bytesUtf8('áéíóú')).toBe(10);
  });

  it('cuenta cuatro bytes por un emoji', () => {
    expect(bytesUtf8('🦷')).toBe(4);
  });
});

describe('esEmailValido', () => {
  it('acepta emails con la forma usual', () => {
    for (const valido of [EMAIL, 'ana.ejemplo+turnos@consultorio.example.com']) {
      expect(esEmailValido(valido), valido).toBe(true);
    }
  });

  it('rechaza valores sin la forma de un email', () => {
    for (const malo of ['', '   ', 'odontologa', 'odontologa@', '@example.com', 'ana ejemplo@example.com', 'ana@@example.com', 'ana@example']) {
      expect(esEmailValido(malo), JSON.stringify(malo)).toBe(false);
    }
  });

  it('rechaza un email de más de 254 caracteres', () => {
    const largo = `${'a'.repeat(243)}@example.com`;
    expect(largo.length).toBe(255);
    expect(esEmailValido(largo)).toBe(false);
  });
});

describe('evaluarRequisitos', () => {
  it('mientras se escribe, una contraseña corta deja el largo pendiente, no fallado', () => {
    const requisitos = evaluarRequisitos('corta1', EMAIL);
    expect(requisitos.largoMinimo).toBe('pendiente');
  });

  it('al enviar, una contraseña corta deja el largo fallado', () => {
    const requisitos = evaluarRequisitos('corta-123', EMAIL, { alEnviar: true });
    expect(requisitos.largoMinimo).toBe('fallado');
  });

  it('con 10 caracteres el largo queda cumplido', () => {
    expect(evaluarRequisitos('ficticia-1', EMAIL).largoMinimo).toBe('cumplido');
    expect(evaluarRequisitos('ficticia-', EMAIL).largoMinimo).toBe('pendiente');
  });

  it('cuenta el largo en unidades de texto como el backend, no en bytes', () => {
    expect(evaluarRequisitos('ñññññññññ', EMAIL).largoMinimo).toBe('pendiente');
    expect(evaluarRequisitos('ññññññññññ', EMAIL).largoMinimo).toBe('cumplido');
  });

  it('una contraseña igual al email falla aunque cambien las mayúsculas', () => {
    expect(evaluarRequisitos(EMAIL, EMAIL).distintaDelEmail).toBe('fallado');
    expect(evaluarRequisitos('Odontologa@Example.com', EMAIL).distintaDelEmail).toBe('fallado');
  });

  it('una contraseña distinta del email cumple; vacía queda pendiente', () => {
    expect(evaluarRequisitos('ficticia-segura-01', EMAIL).distintaDelEmail).toBe('cumplido');
    expect(evaluarRequisitos('', EMAIL).distintaDelEmail).toBe('pendiente');
  });

  it('sin email conocido (link de recuperación) el requisito del email no se evalúa', () => {
    expect(evaluarRequisitos('ficticia-segura-01', null).distintaDelEmail).toBeNull();
  });

  it('el requisito de 72 bytes aparece solo cuando se pasa', () => {
    expect(evaluarRequisitos('a'.repeat(72), EMAIL).hasta72Bytes).toBeNull();
    expect(evaluarRequisitos('a'.repeat(73), EMAIL).hasta72Bytes).toBe('fallado');
  });

  it('las letras con tilde cuentan doble para el tope de 72 bytes', () => {
    // 36 "ñ" = 72 bytes (entra); 36 "ñ" + "a" = 73 bytes (se pasa) aunque sean 37 caracteres.
    expect(evaluarRequisitos('ñ'.repeat(36), EMAIL).hasta72Bytes).toBeNull();
    expect(evaluarRequisitos(`${'ñ'.repeat(36)}a`, EMAIL).hasta72Bytes).toBe('fallado');
  });
});

describe('cumpleRequisitos', () => {
  it('acepta una contraseña que cumple RN-AU-01', () => {
    expect(cumpleRequisitos('ficticia-segura-01', EMAIL)).toBe(true);
  });

  it('rechaza corta, igual al email o de más de 72 bytes', () => {
    expect(cumpleRequisitos('corta-123', EMAIL)).toBe(false);
    expect(cumpleRequisitos(EMAIL.toUpperCase(), EMAIL)).toBe(false);
    expect(cumpleRequisitos('a'.repeat(73), EMAIL)).toBe(false);
  });

  it('sin email conocido valida solo el largo y los bytes', () => {
    expect(cumpleRequisitos('ficticia-segura-01', null)).toBe(true);
    expect(cumpleRequisitos('corta-123', null)).toBe(false);
  });
});

describe('validarRepeticion', () => {
  it('no marca nada cuando coinciden', () => {
    expect(validarRepeticion('ficticia-segura-01', 'ficticia-segura-01')).toBeNull();
  });

  it('marca la repetición con el texto del mockup cuando no coinciden', () => {
    expect(validarRepeticion('ficticia-segura-01', 'ficticia-segura-02')).toBe('Las dos contraseñas no coinciden.');
  });
});

describe('validarRegistro', () => {
  const completo: DatosRegistro = {
    nombre: 'Ana',
    apellido: 'Ejemplo',
    email: EMAIL,
    password: 'ficticia-segura-01',
    nombreConsultorio: 'Consultorio Demo',
    aceptaTerminos: true,
  };

  it('no devuelve errores con todos los datos válidos', () => {
    expect(validarRegistro(completo)).toEqual([]);
  });

  it('marca los términos sin aceptar con el texto del mockup', () => {
    expect(validarRegistro({ ...completo, aceptaTerminos: false })).toEqual([
      { campo: 'aceptaTerminos', mensaje: 'Tenés que aceptar los términos y la política de privacidad.' },
    ]);
  });

  it('marca la contraseña corta con el texto del mockup', () => {
    expect(validarRegistro({ ...completo, password: 'corta-123' })).toEqual([
      { campo: 'password', mensaje: 'La contraseña necesita al menos 10 caracteres.' },
    ]);
  });

  it('devuelve los errores en el orden de los campos del formulario, para el resumen', () => {
    const errores = validarRegistro({
      nombre: '  ',
      apellido: '',
      email: 'odontologa',
      password: 'corta-123',
      nombreConsultorio: '',
      aceptaTerminos: false,
    });
    expect(errores.map((error) => error.campo)).toEqual([
      'nombre',
      'apellido',
      'email',
      'password',
      'nombreConsultorio',
      'aceptaTerminos',
    ]);
    for (const error of errores) {
      expect(error.mensaje.length, error.campo).toBeGreaterThan(0);
    }
  });

  it('marca la contraseña igual al email y la de más de 72 bytes', () => {
    expect(validarRegistro({ ...completo, password: EMAIL }).map((e) => e.campo)).toEqual(['password']);
    expect(validarRegistro({ ...completo, password: 'a'.repeat(73) }).map((e) => e.campo)).toEqual(['password']);
  });
});
