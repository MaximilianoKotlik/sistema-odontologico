/**
 * Validaciones puras de los formularios de auth (C-07, design D1). Replican RN-AU-01 tal como la aplica el backend
 * (`PoliticaDeContrasena`): largo en unidades de texto, tope en bytes UTF-8 y comparación con el email sin
 * distinguir mayúsculas. El backend sigue siendo la fuente de verdad; esto solo evita idas y vueltas.
 */

export const LARGO_MINIMO = 10;
export const BYTES_MAXIMOS = 72;
const LARGO_MAXIMO_EMAIL = 254;

export type EstadoRequisito = 'pendiente' | 'cumplido' | 'fallado';

export interface RequisitosContrasena {
  readonly largoMinimo: EstadoRequisito;
  /** `null` cuando no se conoce el email (contraseña nueva desde el link de recuperación). */
  readonly distintaDelEmail: EstadoRequisito | null;
  /** `null` mientras no se pasa del tope: el requisito se muestra solo cuando falla. */
  readonly hasta72Bytes: 'fallado' | null;
}

export interface DatosRegistro {
  readonly nombre: string;
  readonly apellido: string;
  readonly email: string;
  readonly password: string;
  readonly nombreConsultorio: string;
  readonly aceptaTerminos: boolean;
}

export type CampoRegistro = keyof DatosRegistro;

export interface ErrorDeCampo<C extends string = CampoRegistro> {
  readonly campo: C;
  readonly mensaje: string;
}

const codificador = new TextEncoder();

export function bytesUtf8(texto: string): number {
  return codificador.encode(texto).length;
}

const FORMA_DE_EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function esEmailValido(email: string): boolean {
  const limpio = email.trim();
  return limpio.length <= LARGO_MAXIMO_EMAIL && FORMA_DE_EMAIL.test(limpio);
}

function igualAlEmail(password: string, email: string): boolean {
  return password.toLowerCase() === email.trim().toLowerCase();
}

export function evaluarRequisitos(
  password: string,
  email: string | null,
  opciones: { readonly alEnviar?: boolean } = {},
): RequisitosContrasena {
  const faltante: EstadoRequisito = opciones.alEnviar === true ? 'fallado' : 'pendiente';
  const largoMinimo: EstadoRequisito = password.length >= LARGO_MINIMO ? 'cumplido' : faltante;

  let distintaDelEmail: EstadoRequisito | null = null;
  if (email !== null) {
    if (password.length === 0) distintaDelEmail = faltante;
    else distintaDelEmail = igualAlEmail(password, email) ? 'fallado' : 'cumplido';
  }

  const hasta72Bytes = bytesUtf8(password) > BYTES_MAXIMOS ? 'fallado' : null;
  return { largoMinimo, distintaDelEmail, hasta72Bytes };
}

export function cumpleRequisitos(password: string, email: string | null): boolean {
  const requisitos = evaluarRequisitos(password, email);
  return (
    requisitos.largoMinimo === 'cumplido' &&
    (requisitos.distintaDelEmail === null || requisitos.distintaDelEmail === 'cumplido') &&
    requisitos.hasta72Bytes === null
  );
}

export function validarRepeticion(nueva: string, repeticion: string): string | null {
  return nueva === repeticion ? null : 'Las dos contraseñas no coinciden.';
}

/** Mensaje del requisito que no se cumple, o `null` si la contraseña es válida. */
export function mensajeDeContrasena(password: string, email: string | null): string | null {
  const requisitos = evaluarRequisitos(password, email, { alEnviar: true });
  if (requisitos.largoMinimo !== 'cumplido') return 'La contraseña necesita al menos 10 caracteres.';
  if (requisitos.hasta72Bytes !== null) return 'La contraseña puede tener hasta 72 caracteres (las letras con tilde y la ñ cuentan doble).';
  if (requisitos.distintaDelEmail === 'fallado') return 'La contraseña no puede ser igual a tu email.';
  return null;
}

/** Errores de formato del registro, en el orden de los campos del formulario (para el resumen). */
export function validarRegistro(datos: DatosRegistro): ErrorDeCampo[] {
  const errores: ErrorDeCampo[] = [];
  if (datos.nombre.trim() === '') errores.push({ campo: 'nombre', mensaje: 'Escribí tu nombre.' });
  if (datos.apellido.trim() === '') errores.push({ campo: 'apellido', mensaje: 'Escribí tu apellido.' });
  if (!esEmailValido(datos.email)) errores.push({ campo: 'email', mensaje: 'Escribí un email válido, por ejemplo nombre@dominio.com.' });
  const errorDeContrasena = mensajeDeContrasena(datos.password, datos.email);
  if (errorDeContrasena !== null) errores.push({ campo: 'password', mensaje: errorDeContrasena });
  if (datos.nombreConsultorio.trim() === '') {
    errores.push({ campo: 'nombreConsultorio', mensaje: 'Escribí el nombre de tu consultorio.' });
  }
  if (!datos.aceptaTerminos) {
    errores.push({ campo: 'aceptaTerminos', mensaje: 'Tenés que aceptar los términos y la política de privacidad.' });
  }
  return errores;
}
