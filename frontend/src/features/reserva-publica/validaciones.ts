/**
 * Validación del paso de datos en el cliente, con las mismas reglas que el backend (C-16, design D5; RN-GL-04).
 * El backend sigue siendo la fuente de verdad: si igual responde 400, el error se muestra en el campo.
 */

export interface DatosDelFormulario {
  readonly nombre: string;
  readonly apellido: string;
  readonly dni: string;
  readonly telefono: string;
  readonly email: string;
  readonly acepta: boolean;
}

export type CampoDelFormulario = 'nombre' | 'apellido' | 'dni' | 'telefono' | 'email' | 'acepta';

export interface ErrorDeCampo {
  readonly campo: CampoDelFormulario;
  /** Texto del resumen de arriba, enlazado al campo. */
  readonly resumen: string;
  /** Texto debajo del campo. */
  readonly mensaje: string;
}

/** Datos del paciente tal como los recibe `POST /api/v1/public/consultorios/{slug}/turnos`. */
export interface DatosDelPaciente {
  readonly nombre: string;
  readonly apellido: string;
  readonly dni: string;
  readonly telefono: string;
  readonly email?: string;
  readonly aceptaPrivacidad: true;
}

export type ResultadoDeValidacion =
  | { readonly ok: true; readonly datos: DatosDelPaciente }
  | { readonly ok: false; readonly errores: readonly ErrorDeCampo[] };

/** Igual que `Paciente.LARGO_MAXIMO_NOMBRE` del backend. */
export const LARGO_MAXIMO_NOMBRE = 80;

export const ERRORES: Readonly<Record<CampoDelFormulario, Omit<ErrorDeCampo, 'campo'>>> = {
  nombre: { resumen: 'Falta tu nombre.', mensaje: 'Escribí tu nombre, de hasta 80 letras.' },
  apellido: { resumen: 'Falta tu apellido.', mensaje: 'Escribí tu apellido, de hasta 80 letras.' },
  dni: { resumen: 'El DNI tiene que tener 7 u 8 números.', mensaje: 'El DNI tiene que tener 7 u 8 números, por ejemplo 30.000.001.' },
  telefono: { resumen: 'Falta la característica en el celular.', mensaje: 'Escribilo con característica, por ejemplo 11 0000-0042.' },
  email: { resumen: 'Revisá el email.', mensaje: 'Revisá el email, por ejemplo ana@example.com. Si no querés dejarlo, borralo.' },
  acepta: { resumen: 'Tenés que aceptar el uso de tus datos.', mensaje: 'Para reservar tenés que aceptarlo.' },
};

/** Mismo criterio que `Dni` del backend: se quitan puntos y espacios y quedan 7 u 8 dígitos. */
export function normalizarDni(texto: string): string | null {
  const limpio = texto.replace(/[.\s]/g, '');
  return /^\d{7,8}$/.test(limpio) ? limpio : null;
}

/**
 * Aproximación del `isPossibleNumber` con región AR que usa el backend (C-02): sin `+`, el número tiene que traer la
 * característica (10 dígitos nacionales, hasta 12 si incluye el 15). Con un prefijo internacional distinto de +54
 * se acepta un largo E.164 posible y decide el backend.
 */
function telefonoPosible(texto: string): boolean {
  const sinSeparadores = texto.trim().replace(/[\s\-().]/g, '');
  if (!/^\+?\d+$/.test(sinSeparadores)) return false;
  if (sinSeparadores.startsWith('+') && !sinSeparadores.startsWith('+54')) {
    const digitos = sinSeparadores.slice(1).length;
    return digitos >= 8 && digitos <= 15;
  }
  let nacional = sinSeparadores.replace(/^\+54/, '');
  if (sinSeparadores.startsWith('+54')) nacional = nacional.replace(/^9/, '');
  nacional = nacional.replace(/^0/, '');
  return nacional.length >= 10 && nacional.length <= 12;
}

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

const error = (campo: CampoDelFormulario): ErrorDeCampo => ({ campo, ...ERRORES[campo] });

export function validarDatos(formulario: DatosDelFormulario): ResultadoDeValidacion {
  const nombre = formulario.nombre.trim();
  const apellido = formulario.apellido.trim();
  const dni = normalizarDni(formulario.dni);
  const telefono = formulario.telefono.trim();
  const email = formulario.email.trim();

  const errores: ErrorDeCampo[] = [];
  if (nombre === '' || nombre.length > LARGO_MAXIMO_NOMBRE) errores.push(error('nombre'));
  if (apellido === '' || apellido.length > LARGO_MAXIMO_NOMBRE) errores.push(error('apellido'));
  if (dni === null) errores.push(error('dni'));
  if (!telefonoPosible(telefono)) errores.push(error('telefono'));
  if (email !== '' && !EMAIL.test(email)) errores.push(error('email'));
  if (!formulario.acepta) errores.push(error('acepta'));

  if (errores.length > 0 || dni === null) return { ok: false, errores };
  return {
    ok: true,
    datos: { nombre, apellido, dni, telefono, aceptaPrivacidad: true, ...(email === '' ? {} : { email }) },
  };
}
